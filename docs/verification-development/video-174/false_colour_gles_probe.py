import os,ctypes as C,re,json
from pathlib import Path
os.environ['EGL_PLATFORM']='surfaceless';os.environ['LIBGL_ALWAYS_SOFTWARE']='1'
e=C.CDLL('libEGL.so.1');I=C.c_int;U=C.c_uint;P=C.c_void_p;F=C.c_float
for name,ret,args in [('eglGetDisplay',P,[P]),('eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)]),('eglBindAPI',U,[U]),('eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)]),('eglCreatePbufferSurface',P,[P,P,C.POINTER(I)]),('eglCreateContext',P,[P,P,P,C.POINTER(I)]),('eglMakeCurrent',U,[P,P,P,P]),('eglGetProcAddress',P,[C.c_char_p])]:
 fn=getattr(e,name);fn.restype=ret;fn.argtypes=args
arr=lambda *v:(I*len(v))(*v)
d=e.eglGetDisplay(None);a=I();b=I();assert e.eglInitialize(d,C.byref(a),C.byref(b))
assert e.eglBindAPI(0x30A0)
config=P();n=I();assert e.eglChooseConfig(d,arr(0x3033,1,0x3040,0x40,0x3024,8,0x3023,8,0x3022,8,0x3038),C.byref(config),1,C.byref(n)) and n.value
surf=e.eglCreatePbufferSurface(d,config,arr(0x3057,8,0x3056,1,0x3038));ctx=e.eglCreateContext(d,config,None,arr(0x3098,3,0x3038));assert ctx and surf and e.eglMakeCurrent(d,surf,surf,ctx)
def gl(name,ret,args):
 p=e.eglGetProcAddress(name.encode());assert p,name
 return C.CFUNCTYPE(ret,*args)(p)
create=gl('glCreateShader',U,[U]);source=gl('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P]);compile_=gl('glCompileShader',None,[U]);get=gl('glGetShaderiv',None,[U,U,C.POINTER(I)]);log=gl('glGetShaderInfoLog',None,[U,I,P,C.c_char_p])
def shader(kind,s):
 obj=create(kind);buf=C.c_char_p(s.encode());source(obj,1,C.byref(buf),None);compile_(obj);ok=I();get(obj,0x8B81,C.byref(ok));l=C.create_string_buffer(32768);log(obj,len(l),None,l);assert ok.value,l.value.decode();return obj

from pathlib import Path
s=Path('recovery/source/PhotonSCamera-main/app/src/main/java/com/hinnka/mycamera/lut/Shaders.kt').read_text()
fragment=s.split('val FRAGMENT_SHADER_FOCUS_PEAKING = """',1)[1].split('""".trimIndent()',1)[0].strip()
v=shader(0x8B31,'#version 300 es\nout vec2 vTexCoord; void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);vTexCoord=p;gl_Position=vec4(p*2.0-1.0,0,1);}')
f=shader(0x8B30,fragment)
make=gl('glCreateProgram',U,[]);attach=gl('glAttachShader',None,[U,U]);link=gl('glLinkProgram',None,[U]);gp=gl('glGetProgramiv',None,[U,U,C.POINTER(I)])
p=make();attach(p,v);attach(p,f);link(p);ok=I();gp(p,0x8B82,C.byref(ok));assert ok.value
use=gl('glUseProgram',None,[U]);use(p);loc=gl('glGetUniformLocation',I,[U,C.c_char_p]);ui=gl('glUniform1i',None,[I,I]);uf=gl('glUniform1f',None,[I,F]);u2=gl('glUniform2f',None,[I,F,F])
gen=gl('glGenTextures',None,[I,C.POINTER(U)]);bind=gl('glBindTexture',None,[U,U]);param=gl('glTexParameteri',None,[U,U,I]);image=gl('glTexImage2D',None,[U,I,I,I,I,I,U,U,P]);texture=U();gen(1,C.byref(texture));bind(0x0DE1,texture)
for k,val in [(0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812F),(0x2803,0x812F)]:param(0x0DE1,k,val)
levels=[0,8,80,108,140,200,245,254];samples=bytes(v for x in levels for v in [x,x,x,255]);array=(C.c_ubyte*len(samples)).from_buffer_copy(samples);image(0x0DE1,0,0x1908,8,1,0,0x1908,0x1401,array)
for k,val in [('uInputTexture',0),('uPeakingEnabled',0),('uZebraEnabled',0)]:ui(loc(p,k.encode()),val)
uf(loc(p,b'uMagnification'),1);u2(loc(p,b'uTexelSize'),1/8,1)
viewport=gl('glViewport',None,[I,I,I,I]);viewport(0,0,8,1);draw=gl('glDrawArrays',None,[U,I,I]);read=gl('glReadPixels',None,[I,I,I,I,U,U,P]);error=gl('glGetError',U,[])
expected=[[128,0,204],[0,51,255],[80,80,80],[0,204,51],[255,102,166],[200,200,200],[255,255,0],[255,0,0]]
report={}
for enabled in [0,1]:
 ui(loc(p,b'uFalseColorEnabled'),enabled);draw(4,0,3);out=(C.c_ubyte*32)();read(0,0,8,1,0x1908,0x1401,out);assert error()==0
 pixels=[list(out[i*4:i*4+3]) for i in range(8)]
 target=expected if enabled else [[x,x,x] for x in levels]
 assert all(abs(a-b)<=1 for pixel,ref in zip(pixels,target) for a,b in zip(pixel,ref)),pixels
 report['enabled' if enabled else 'disabled']=pixels
print(json.dumps({'scope':'Actual complete preview-assist shader GLES compile/link/draw/readback on host software GPU; no Android/device validation','result':report},indent=2))
