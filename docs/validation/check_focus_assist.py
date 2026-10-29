import ctypes as C, re, textwrap, json, os
from pathlib import Path
os.environ['EGL_PLATFORM']='surfaceless'
e=C.CDLL('libEGL.so.1'); P=C.c_void_p; I=C.c_int; U=C.c_uint; F=C.c_float
def egl(n,rest,args):
 f=getattr(e,n);f.restype=rest;f.argtypes=args;return f
d=egl('eglGetDisplay',P,[P])(None); ma=I();mi=I()
assert egl('eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)])(d,C.byref(ma),C.byref(mi))
assert egl('eglBindAPI',U,[U])(0x30A0)
a=(I*13)(0x3033,1,0x3040,0x40,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3038);cfg=P();n=I()
assert egl('eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)])(d,a,C.byref(cfg),1,C.byref(n)) and n.value
ctx=egl('eglCreateContext',P,[P,P,P,C.POINTER(I)])(d,cfg,None,(I*3)(0x3098,3,0x3038));assert ctx
surf=egl('eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(d,cfg,(I*5)(0x3057,12,0x3056,2,0x3038));assert surf
assert egl('eglMakeCurrent',U,[P,P,P,P])(d,surf,surf,ctx)
proc=egl('eglGetProcAddress',P,[C.c_char_p])
def gl(n,r,*a):return C.CFUNCTYPE(r,*a)(proc(n.encode()))
create=gl('glCreateShader',U,U);source=gl('glShaderSource',None,U,I,C.POINTER(C.c_char_p),P);compile_=gl('glCompileShader',None,U);param=gl('glGetShaderiv',None,U,U,C.POINTER(I))
def shader(kind,src):
 sh=create(kind);buf=C.c_char_p(src.encode());source(sh,1,C.byref(buf),None);compile_(sh);ok=I();param(sh,0x8B81,C.byref(ok))
 if not ok.value:
  out=C.create_string_buffer(8192);gl('glGetShaderInfoLog',None,U,I,P,P)(sh,8192,None,out);raise AssertionError(out.value.decode())
 return sh
s=Path('app/src/main/java/com/hinnka/mycamera/lut/Shaders.kt').read_text()
frag=textwrap.dedent(re.search(r'val FRAGMENT_SHADER_FOCUS_PEAKING = """(.*?)"""',s,re.S)[1]).strip()
v='''#version 300 es
out vec2 vTexCoord;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));vTexCoord=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}'''
vs=shader(0x8B31,v);fs=shader(0x8B30,frag);program=gl('glCreateProgram',U)();attach=gl('glAttachShader',None,U,U);attach(program,vs);attach(program,fs);gl('glLinkProgram',None,U)(program);ok=I();gl('glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(ok));assert ok.value
# Also compile the actual production vertex shader to detect incompatible syntax.
production_v=textwrap.dedent(re.search(r'val SIMPLE_VERTEX_SHADER = """(.*?)"""',s,re.S)[1]).strip();shader(0x8B31,production_v)
gl('glUseProgram',None,U)(program)
loc=gl('glGetUniformLocation',I,U,C.c_char_p)
def ui(n,v):gl('glUniform1i',None,I,I)(loc(program,n.encode()),v)
def uf(n,v):gl('glUniform1f',None,I,F)(loc(program,n.encode()),v)
tex=U();gl('glGenTextures',None,I,C.POINTER(U))(1,C.byref(tex));gl('glBindTexture',None,U,U)(0x0DE1,tex.value)
for key,val in [(0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812F),(0x2803,0x812F)]:gl('glTexParameteri',None,U,U,I)(0x0DE1,key,val)
ui('uInputTexture',0);ui('uPeakingEnabled',0);uf('uThreshold',.07);uf('uZebraThreshold',.95)
gl('glUniform2f',None,I,F,F)(loc(program,b'uTexelSize'),1/12,1/2);gl('glUniform3f',None,I,F,F,F)(loc(program,b'uPeakColor'),1,.1,.1)
gl('glViewport',None,I,I,I,I)(0,0,12,2)
def render(value,zebra):
 data=(C.c_ubyte*96)(*([value,value,value,255]*24));gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x1908,12,2,0,0x1908,0x1401,data);ui('uZebraEnabled',int(zebra));gl('glDrawArrays',None,U,I,I)(4,0,3);out=(C.c_ubyte*96)();gl('glReadPixels',None,I,I,I,I,U,U,P)(0,0,12,2,0x1908,0x1401,out);assert gl('glGetError',U)()==0;return list(out)
for value in [0,128,240,255]:
 out=render(value,False);assert all(abs(out[i]-value)<=1 for i in range(0,96,4)),(value,out)
for value in [0,128,240]:
 out=render(value,True);assert all(abs(out[i]-value)<=1 for i in range(0,96,4))
out=render(255,True);vals=set(out[::4]);assert len(vals)==2 and min(vals)<100 and max(vals)==255,vals
assert set(out[3::4])=={255}
# Threshold boundary checks on flat grayscale frames.
for threshold in [.70,.95,1.0]:
 uf('uZebraThreshold',threshold)
 assert len(set(render(255,True)[::4]))==2
print(json.dumps({'shader_compile':'production fragment and vertex passed','render_checks':'disabled pass-through, dark/mid/below-threshold identity, white stripes, alpha, threshold 70/95/100 passed','EGL':f'{ma.value}.{mi.value}','limitation':'desktop Mesa GLES does not validate Android or CMF Mali driver behaviour'},indent=2))
# A low-contrast edge activates high sensitivity but not low sensitivity.
def edge_render(threshold,color):
 data=(C.c_ubyte*96)(*sum(([v,v,v,255] for y in range(2) for x in range(12) for v in [80 if x<6 else 105]),[]))
 gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x1908,12,2,0,0x1908,0x1401,data)
 ui('uPeakingEnabled',1);ui('uZebraEnabled',0);uf('uThreshold',threshold)
 gl('glUniform3f',None,I,F,F,F)(loc(program,b'uPeakColor'),*color)
 gl('glDrawArrays',None,U,I,I)(4,0,3);out=(C.c_ubyte*96)();gl('glReadPixels',None,I,I,I,I,U,U,P)(0,0,12,2,0x1908,0x1401,out);assert gl('glGetError',U)()==0;return list(out)
low=edge_render(.14,(1,.1,.1));high=edge_render(.025,(1,.1,.1));assert low!=high
medium=edge_render(.07,(1,.1,.1));assert medium!=low
for rgb in [(1,.1,.1),(1,1,0),(0,.5,1),(1,1,1)]:
 out=edge_render(.025,rgb);assert out!=low and set(out[3::4])=={255}
print('Peaking edge sensitivity and four colour render checks passed')
# Magnification samples only the centre of the input; the source texture stays unchanged.
ui('uPeakingEnabled',0);ui('uZebraEnabled',0)
data=(C.c_ubyte*96)(*sum(([x*20,x*20,x*20,255] for y in range(2) for x in range(12)),[]))
gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x1908,12,2,0,0x1908,0x1401,data)
for factor in [1,2,4]:
 uf('uMagnification',float(factor));gl('glDrawArrays',None,U,I,I)(4,0,3)
 out=(C.c_ubyte*96)();gl('glReadPixels',None,I,I,I,I,U,U,P)(0,0,12,2,0x1908,0x1401,out)
 expected=[int(((x+.5)/12-.5)/factor*12+6)*20 for x in range(12)]
 assert all(abs(a-b)<=1 for a,b in zip(list(out)[0:48:4],expected)),(factor,list(out)[0:48:4],expected)
 assert set(out[3::4])=={255}
assert gl('glGetError',U)()==0
print('Centre magnification pixel probes passed at 1x, 2x and 4x')
