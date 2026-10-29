"""Reference preview transfer: real host GLES draw/readback, not phone validation."""
import ctypes as C
import hashlib,json,re,struct,sys,textwrap,zipfile
from pathlib import Path
import numpy as np
root=Path(__file__).resolve().parents[2]
base=root/'app/src/main/java/com/hinnka/mycamera'
def literal(path,key):
    return textwrap.dedent(re.search(key+r'\s*=\s*"""(.*?)"""',(base/path).read_text(),re.S)[1]).strip()
log=literal(Path('lut/LogInputGl.kt'),'GLSL')
inverse=log.split('vec3 prepareLogLinearInput',1)[0]
core=literal(Path('lut/PreviewColorShaderModules.kt'),'COLOR_TRANSFER_CORE')
curve=(base/'raw/ACR3Curve.kt').read_text().split('floatArrayOf(',1)[1].split(')',1)[0]
values=np.array([float(v) for v in re.findall(r'([0-9.]+)f',curve)],np.float32)
report={}
if len(sys.argv)>1:
    apk=Path(sys.argv[1]);sha=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()
    archived=json.loads((root/'docs/build-verification-173.json').read_text())
    assert sha==archived['apk_sha256']
    with zipfile.ZipFile(apk) as z:
        dex=[z.read(n) for n in z.namelist() if n.endswith('.dex')]
    assert any(values.astype('<f4').tobytes() in b for b in dex)
    normalized=[' '.join(b.decode('utf8','replace').split()) for b in dex]
    assert any(' '.join(inverse.split()) in b for b in normalized)
    for step in ['vec3 linear = srgbToLinear(clamp(sampled.rgb, 0.0, 1.0));',
                 'vec3 scene = vec3(inverseAcr3(linear.r), inverseAcr3(linear.g), inverseAcr3(linear.b));']:
        assert any(step.encode() in b for b in dex)
        assert step in (base/'lut/RawEnginePreviewGl.kt').read_text()
    report.update(reference_apk_sha256=sha,exact_archived_build=173,exact_acr3_samples=len(values),inverse_shader_matches=True)
controller=(base/'camera/Camera2Controller.kt').read_text()
assert 'applyEnginePreviewTonemap' not in controller and 'enginePreviewTonemapDefaults' not in controller
raw=(base/'lut/RawEnginePreviewGl.kt').read_text()
assert 'private val inverseAcr = LogInputGl()' in raw
assert '4, plan.ispToneCurve)' not in raw
# Reuse the existing host EGL context/compiler harness (no production shader replacement).
harness=(root/'scripts/validation/preview_assistance_gl.py').read_text().split("s=Path('app/",1)[0]
exec(compile(harness,'host_egl_harness','exec'))
vs=shader(0x8B31,'#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0,1);}')
fs=shader(0x8B30,'#version 300 es\nprecision highp float;precision highp int;uniform vec3 sampleColor;out vec4 fragColor;\n'+core+'\n'+inverse+'''\nvoid main(){
vec3 linear=srgbToLinear(clamp(sampleColor,0.0,1.0));
vec3 scene=vec3(inverseAcr3(linear.r),inverseAcr3(linear.g),inverseAcr3(linear.b));
fragColor=vec4(linearToSrgb(scene),1.0);}
''')
p=gl('glCreateProgram',U)();attach=gl('glAttachShader',None,U,U)
attach(p,vs);attach(p,fs);gl('glLinkProgram',None,U)(p);ok=I();gl('glGetProgramiv',None,U,U,C.POINTER(I))(p,0x8B82,C.byref(ok));assert ok.value
gl('glUseProgram',None,U)(p)
table=np.interp(np.linspace(0,1,4096),values,np.linspace(0,1,len(values))).astype(np.float32)
t=U();gl('glGenTextures',None,I,C.POINTER(U))(1,C.byref(t))
gl('glActiveTexture',None,U)(0x84C4);gl('glBindTexture',None,U,U)(0x0DE1,t.value)
gl('glTexStorage2D',None,U,I,U,I,I)(0x0DE1,1,0x822E,4096,1)
for key,val in [(0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812F),(0x2803,0x812F)]:gl('glTexParameteri',None,U,U,I)(0x0DE1,key,val)
gl('glTexSubImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0,0,4096,1,0x1903,0x1406,table.ctypes.data)
loc=gl('glGetUniformLocation',I,U,C.c_char_p)
gl('glUniform1i',None,I,I)(loc(p,b'uInverseAcr3Texture'),4)
color=loc(p,b'sampleColor');error=0
samples=[(i/255,)*3 for i in range(256)]+[(1,.2,.05),(.03,.7,.9)]
for rgb in samples:
    gl('glUniform3f',None,I,F,F,F)(color,*rgb);gl('glDrawArrays',None,U,I,I)(4,0,3)
    out=(C.c_ubyte*4)();gl('glReadPixels',None,I,I,I,I,U,U,P)(0,0,1,1,0x1908,0x1401,out)
    encoded=np.array(rgb);linear=np.where(encoded<=.04045,encoded/12.92,((encoded+.055)/1.055)**2.4)
    scene=np.interp(linear,np.linspace(0,1,4096),table)
    expected=np.where(scene<=.0031308,scene*12.92,1.055*scene**(1/2.4)-.055)
    err=np.abs(np.array(out[:3])-np.round(expected*255)).max();error=max(error,int(err));assert err<=1,(rgb,out[:],expected)
    assert out[3]==255
assert gl('glGetError',U)()==0
report.update(gles_draw_samples=len(samples),max_rgba8_error=error,camera_tonemap_override_removed=True,limitation='Host Mesa GLES only; CMF phone validation pending. ACR3 is a reference look, not a calibrated CMF ISP inverse.')
print(json.dumps(report,indent=2))
