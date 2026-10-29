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

src=Path('app/src/main/java/com/hinnka/mycamera/lut/LutDomainShader.kt').read_text();glsl=textwrap.dedent(re.search(r'val GLSL = """(.*?)"""',src,re.S)[1]).strip()
v='#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0.0,1.0);}'
f='#version 300 es\nprecision highp float;\nprecision highp sampler3D;\n'+glsl+'\nuniform sampler3D uLutTexture; uniform vec3 uInput; out vec4 fragColor; void main(){vec3 uv=normalizeLutDomain(uInput)*0.5+0.25;fragColor=vec4(texture(uLutTexture,uv).rgb,1.0);}'
vs=shader(0x8B31,v);fs=shader(0x8B30,f);program=gl('glCreateProgram',U)();attach=gl('glAttachShader',None,U,U);attach(program,vs);attach(program,fs);gl('glLinkProgram',None,U)(program);ok=I();gl('glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(ok));assert ok.value
gl('glUseProgram',None,U)(program);gl('glViewport',None,I,I,I,I)(0,0,1,1)
loc=gl('glGetUniformLocation',I,U,C.c_char_p)
def vec(n,v):gl('glUniform3f',None,I,F,F,F)(loc(program,n.encode()),*v)
tex=U();gl('glGenTextures',None,I,C.POINTER(U))(1,C.byref(tex));gl('glBindTexture',None,U,U)(0x806F,tex.value)
for key,val in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F),(0x8072,0x812F)]:gl('glTexParameteri',None,U,U,I)(0x806F,key,val)
data=(C.c_ubyte*24)(*sum(([r*255,g*255,b*255] for b in range(2) for g in range(2) for r in range(2)),[]));gl('glPixelStorei',None,U,I)(0x0CF5,1);gl('glTexImage3D',None,U,I,I,I,I,I,I,U,U,P)(0x806F,0,0x8051,2,2,2,0,0x1907,0x1401,data)
gl('glUniform1i',None,I,I)(loc(program,b'uLutTexture'),0)
def render(rgb,lo,hi):
 vec('uInput',rgb);vec('uLutDomainMin',lo);vec('uLutDomainMax',hi)
 gl('glDrawArrays',None,U,I,I)(4,0,3);out=(C.c_ubyte*4)();gl('glReadPixels',None,I,I,I,I,U,U,P)(0,0,1,1,0x1908,0x1401,out);assert gl('glGetError',U)()==0;return [v/255 for v in out[:3]]
checks=0
for lo,hi in [((0,0,0),(1,1,1)),((.25,.25,.25),(.75,.75,.75)),((-.2,.1,.3),(.8,.9,.7))]:
 for rgb in [(0,0,0),(.25,.25,.25),(.375,.5,.625),(.5,.5,.5),(.75,.75,.75),(1,1,1),(.1,.3,.6)]:
  out=render(rgb,lo,hi);want=[max(0,min(1,(a-l)/(h-l))) for a,l,h in zip(rgb,lo,hi)];assert max(abs(a-b) for a,b in zip(out,want))<=1/255,(rgb,lo,hi,out,want);checks+=1
print(json.dumps({'result':'PASS','GLES':'Mesa EGL '+str(ma.value)+'.'+str(mi.value),'actual_texture':'2x2x2 RGB8 identity lattice; hardware trilinear interpolation','domains':['unit','0.25..0.75','different per-channel bounds'],'numeric_readbacks':checks,'boundary_clamp_and_knots':'PASS'},indent=2))
