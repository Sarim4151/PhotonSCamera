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

src=Path('app/src/main/java/com/hinnka/mycamera/lut/PhotographicStyleShader.kt').read_text()
glsl=textwrap.dedent(re.search(r'val GLSL = """(.*?)"""',src,re.S)[1]).strip()
v='#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0.0,1.0);}'
f='#version 300 es\nprecision highp float;\n'+glsl+'\nuniform vec3 uColour; out vec4 fragColor; void main(){fragColor=vec4(applyPhotographicStyle(uColour),1.0);}'
vs=shader(0x8B31,v);fs=shader(0x8B30,f);program=gl('glCreateProgram',U)();attach=gl('glAttachShader',None,U,U);attach(program,vs);attach(program,fs);gl('glLinkProgram',None,U)(program);ok=I();gl('glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(ok));assert ok.value
gl('glUseProgram',None,U)(program);gl('glViewport',None,I,I,I,I)(0,0,1,1)
loc=gl('glGetUniformLocation',I,U,C.c_char_p)
def render(rgb,style):
 for name,val in [('uColour',rgb),('uPhotographicStyle',style)]: gl('glUniform3f',None,I,F,F,F)(loc(program,name.encode()),*val)
 gl('glDrawArrays',None,U,I,I)(4,0,3);out=(C.c_ubyte*4)();gl('glReadPixels',None,I,I,I,I,U,U,P)(0,0,1,1,0x1908,0x1401,out);assert gl('glGetError',U)()==0;return [v/255 for v in out[:3]]
patches=[(0,0,0),(.18,.18,.18),(.5,.5,.5),(.95,.95,.95),(1,1,1),(.65,.45,.30),(.30,.45,.65),(.8,.12,.15)]
for rgb in patches:
 out=render(rgb,(0,0,0));assert max(abs(a-b) for a,b in zip(rgb,out))<=1/255,(rgb,out)
for rgb in patches:
 for style in [(1,1,1),(-1,-1,-1),(.2,-.4,.6)]:
  out=render(rgb,style);assert all(0<=v<=1 for v in out)
# Tone preserves neutral hue and white/black points, independently of exposure.
for y in [0,.18,.5,.95,1]:
 for tone in [-1,1]:
  out=render((y,y,y),(tone,0,0));assert max(out)-min(out)<1/255
  if y in [0,1]:assert abs(out[0]-y)<1/255
skin=(.65,.45,.30);cool=(.30,.45,.65)
skin_delta=sum(abs(a-b) for a,b in zip(render(skin,(0,1,0)),render(skin,(0,0,0))))
cool_delta=sum(abs(a-b) for a,b in zip(render(cool,(0,1,0)),render(cool,(0,0,0))))
assert skin_delta<cool_delta*.8,(skin_delta,cool_delta)
# Production paths all call identical module exactly once, before contrast and log encoding.
for file in ['PreviewColorShader.kt','LutImageProcessor.kt','VideoLutEffect.kt']:
 text=Path('app/src/main/java/com/hinnka/mycamera/lut/'+file).read_text()
 assert text.count('${PhotographicStyleShader.GLSL}')==1,file
 assert text.count('color.rgb = applyPhotographicStyle(color.rgb);')==1,file
 assert text.index('color.rgb = applyPhotographicStyle(color.rgb);')<text.index('color.rgb = applyContrastSCurve(color.rgb, uContrast);'),file
print(json.dumps({'GLES':'Mesa EGL '+str(ma.value)+'.'+str(mi.value),'result':'PASS','neutral_identity_patches':len(patches),'bounded_extremes':len(patches)*3,'tone_neutral_hue_and_endpoints':'PASS','warmth_skin_delta':skin_delta,'warmth_cool_delta':cool_delta,'production_module_and_order':'preview/still/export identical, one insertion each','limitation':'module compile/link/draw/readback on desktop Mesa; Android camera sensor and driver validation pending'},indent=2))
