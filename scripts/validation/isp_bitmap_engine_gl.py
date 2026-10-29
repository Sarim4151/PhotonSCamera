import ctypes as C, os, re
from pathlib import Path
import numpy as np
os.environ['EGL_PLATFORM']='surfaceless'
e=C.CDLL('libEGL.so.1'); g=C.CDLL('libGL.so.1')
def fn(lib,n,ret,args):
 f=getattr(lib,n); f.restype=ret; f.argtypes=args; return f
I=C.c_int; U=C.c_uint; P=C.c_void_p; F=C.c_float
get=fn(e,'eglGetDisplay',P,[P]); d=get(None)
assert fn(e,'eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)])(d,C.byref(I()),C.byref(I()))
fn(e,'eglBindAPI',U,[U])(0x30A0)
a=(I*13)(0x3040,0x40,0x3024,8,0x3023,8,0x3022,8,0x3033,1,0x3038,0,0); cfg=P(); n=I()
assert fn(e,'eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)])(d,a,C.byref(cfg),1,C.byref(n)) and n.value
ctx=fn(e,'eglCreateContext',P,[P,P,P,C.POINTER(I)])(d,cfg,None,(I*3)(0x3098,3,0x3038))
surf=fn(e,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)])(d,cfg,(I*5)(0x3057,1,0x3056,1,0x3038))
assert fn(e,'eglMakeCurrent',U,[P,P,P,P])(d,surf,surf,ctx)
print(fn(g,'glGetString',C.c_char_p,[U])(0x1F02).decode())
base=Path(__file__).resolve().parents[2]/'app/src/main/java/com/hinnka/mycamera'
def literal(file,key):
 t=(base/file).read_text(); return re.search(key+r'\s*=\s*"""(.*?)"""',t,re.S)[1].strip()
frag=literal(Path('lut/IspBitmapEngineGl.kt'),'FRAGMENT_SHADER')
frag=frag.replace('${PreviewColorShaderModules.COLOR_TRANSFER_CORE}',literal(Path('lut/PreviewColorShaderModules.kt'),'COLOR_TRANSFER_CORE'))
fragment_sampling=literal(Path('lut/RawEnginePreviewGl.kt'),'SAMPLING_GLSL')
for a,b in {'$TILES_PER_ROW':'9','$GRID_SIZE':'65','$ATLAS_WIDTH':'585','$ATLAS_HEIGHT':'520','${GRID_SIZE - 1}':'64'}.items(): fragment_sampling=fragment_sampling.replace(a,b)
frag=frag.replace('${RawEnginePreviewGl.SAMPLING_GLSL}',fragment_sampling)
vert=literal(Path('raw/RawFullscreenQuad.kt'),'VERTEX_SHADER')
def shader(kind,src):
 s=fn(g,'glCreateShader',U,[U])(kind); b=src.encode(); ptr=C.c_char_p(b)
 fn(g,'glShaderSource',None,[U,I,C.POINTER(C.c_char_p),P])(s,1,C.byref(ptr),None); fn(g,'glCompileShader',None,[U])(s)
 ok=I();fn(g,'glGetShaderiv',None,[U,U,C.POINTER(I)])(s,0x8B81,C.byref(ok))
 if not ok.value:
  buf=C.create_string_buffer(8192);fn(g,'glGetShaderInfoLog',None,[U,I,P,P])(s,8192,None,buf);raise RuntimeError(buf.value)
 return s
p=fn(g,'glCreateProgram',U,[])()
for s in [shader(0x8B31,vert),shader(0x8B30,frag)]:fn(g,'glAttachShader',None,[U,U])(p,s)
fn(g,'glLinkProgram',None,[U])(p); ok=I();fn(g,'glGetProgramiv',None,[U,U,C.POINTER(I)])(p,0x8B82,C.byref(ok));assert ok.value
fn(g,'glUseProgram',None,[U])(p)
width,height=37,19
rng=np.random.default_rng(9); inp=rng.integers(0,256,(height,width,4),dtype=np.uint8);inp[:,:,3]=255
atlas=np.zeros((520,585,4),np.float32)
for b in range(65):
 for y in range(65):
  for x in range(65):
   sr=np.array([x,y,b],np.float32)/64
   atlas[b//9*65+y,b%9*65+x,:3]=np.where(sr<=.04045,sr/12.92,((sr+.055)/1.055)**2.4);atlas[b//9*65+y,b%9*65+x,3]=1
textures=[]
def tex(unit,w,h,internal,data,typ):
 out=U();fn(g,'glGenTextures',None,[I,C.POINTER(U)])(1,C.byref(out));textures.append(out.value)
 fn(g,'glActiveTexture',None,[U])(0x84C0+unit);fn(g,'glBindTexture',None,[U,U])(0x0DE1,out)
 fn(g,'glTexStorage2D',None,[U,I,U,I,I])(0x0DE1,1,internal,w,h)
 if data is not None:fn(g,'glTexSubImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0,0,w,h,0x1908,typ,data.ctypes.data)
 for key,val in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F)]:fn(g,'glTexParameteri',None,[U,U,I])(0x0DE1,key,val)
 return out.value
src=tex(0,width,height,0x8058,inp,0x1401);lut=tex(5,585,520,0x881A,atlas,0x1406);out=tex(1,width,height,0x8058,None,0x1401)
fbo=U();fn(g,'glGenFramebuffers',None,[I,C.POINTER(U)])(1,C.byref(fbo));fn(g,'glBindFramebuffer',None,[U,U])(0x8D40,fbo)
fn(g,'glFramebufferTexture2D',None,[U,U,U,U,I])(0x8D40,0x8CE0,0x0DE1,out,0)
assert fn(g,'glCheckFramebufferStatus',U,[U])(0x8D40)==0x8CD5
for name,unit in [('uInputTexture',0),('uEnginePreviewLut',5)]:fn(g,'glUniform1i',None,[I,I])(fn(g,'glGetUniformLocation',I,[U,C.c_char_p])(p,name.encode()),unit)
identity=np.eye(4,dtype=np.float32);fn(g,'glUniformMatrix4fv',None,[I,I,U,P])(fn(g,'glGetUniformLocation',I,[U,C.c_char_p])(p,b'uTexMatrix'),1,0,identity.ctypes.data)
vertices=np.array([-1,-1,0,0,1,-1,1,0,-1,1,0,1,1,1,1,1],np.float32)
vbo=U();fn(g,'glGenBuffers',None,[I,C.POINTER(U)])(1,C.byref(vbo));fn(g,'glBindBuffer',None,[U,U])(0x8892,vbo)
fn(g,'glBufferData',None,[U,C.c_ssize_t,P,U])(0x8892,vertices.nbytes,vertices.ctypes.data,0x88E4)
for name,offset in [('aPosition',0),('aTexCoord',8)]:
 loc=fn(g,'glGetAttribLocation',I,[U,C.c_char_p])(p,name.encode());fn(g,'glEnableVertexAttribArray',None,[U])(loc);fn(g,'glVertexAttribPointer',None,[U,I,U,U,I,P])(loc,2,0x1406,0,16,offset)
fn(g,'glViewport',None,[I,I,I,I])(0,0,width,height);fn(g,'glDrawArrays',None,[U,I,I])(5,0,4)
result=np.zeros_like(inp);fn(g,'glReadPixels',None,[I,I,I,I,U,U,P])(0,0,width,height,0x1908,0x1401,result.ctypes.data)
assert fn(g,'glGetError',U,[])()==0
err=np.abs(result.astype(int)-inp.astype(int));assert err.max()<=1,(err.max(),result[0,0],inp[0,0])
assert np.array_equal(result[:,:,3],inp[:,:,3])
print('Actual helper shader compile/link/RGBA16F atlas allocation/bind/draw/readback PASS; 37x19 asymmetric random image, max identity error',err.max(),'of 255; alpha and row orientation preserved.')
fn(e,'eglMakeCurrent',U,[P,P,P,P])(d,None,None,None)
fn(e,'eglDestroySurface',U,[P,P])(d,surf);fn(e,'eglDestroyContext',U,[P,P])(d,ctx);fn(e,'eglTerminate',U,[P])(d)
