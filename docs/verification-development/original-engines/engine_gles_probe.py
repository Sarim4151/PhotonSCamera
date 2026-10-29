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
v=shader(0x8B31,'#version 300 es\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}')
s=(Path(__file__).resolve().parents[3] / 'app/src/main/java/com/hinnka/mycamera/raw/OriginalToneAlgorithms.kt').read_text();common=s.split('private val common = """',1)[1].split('""".trimIndent()',1)[0]
make=gl('glCreateProgram',U,[]);attach=gl('glAttachShader',None,[U,U]);link=gl('glLinkProgram',None,[U]);gp=gl('glGetProgramiv',None,[U,U,C.POINTER(I)]);use=gl('glUseProgram',None,[U]);loc=gl('glGetUniformLocation',I,[U,C.c_char_p]);uniform=gl('glUniform3f',None,[I,F,F,F]);draw=gl('glDrawArrays',None,[U,I,I]);read=gl('glReadPixels',None,[I,I,I,I,U,U,P]);viewport=gl('glViewport',None,[I,I,I,I]);viewport(0,0,8,1)
string=gl('glGetString',C.c_char_p,[U]);error=gl('glGetError',U,[])
sony_body=s.split('val SONY_NATURAL = definition("""',1)[1].split('""".trimIndent())',1)[0]
common += '\nconst mat3 uOutputTransform = mat3(1.0);\nvec3 sonyNaturalProbe(vec3 color){'+sony_body+'\n}'
results={}
for name,body in [('SonyNatural','sonyNaturalProbe(inputColor)'),('NeutralTone','originalTone(inputColor,1.0,1.0)'),('SoftPortrait','originalTone(inputColor,0.88,0.92)'),('SilverMonochrome','originalTone(vec3(max(dot(inputColor, ORIGINAL_LUMA),0.0)),1.18,0.0)')]:
 f=shader(0x8B30,'#version 300 es\nprecision highp float;uniform vec3 inputColor;out vec4 fragColor;\n'+common+'\nvoid main(){fragColor=vec4('+body+',1.0);}')
 p=make();attach(p,v);attach(p,f);link(p);ok=I();gp(p,0x8B82,C.byref(ok));assert ok.value;use(p);u=loc(p,b'inputColor');pixels=[]
 for rgb in [(0,0,0),(.18,.18,.18),(1,1,1),(100,100,100),(4,.2,.05),(-.1,.6,.1)]:
  uniform(u,*rgb);draw(4,0,3);buf=(C.c_ubyte*4)();read(4,0,1,1,0x1908,0x1401,buf);assert error()==0;pixels.append(list(buf)[:3])
 assert pixels[0]==[0,0,0]
 assert all(abs(x-46)<=1 for x in pixels[1]),pixels
 assert pixels[1][0]<pixels[2][0]<pixels[3][0]
 if name=='SilverMonochrome':assert all(max(v)-min(v)<=1 for v in pixels)
 results[name]=pixels
report={'renderer':string(0x1F01).decode(),'api':string(0x1F02).decode(),'scope':'Actual original-operator GLES shader draw/readback on host software GPU; not Android device validation. RGBA8 quantization.','samples':results}
print(json.dumps(report,indent=2))
