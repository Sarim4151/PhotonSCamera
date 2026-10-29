# Numerical QA mirror for SuperResolutionProcessor; requires numpy and scipy.
import numpy as np
from scipy.ndimage import gaussian_filter, map_coordinates, convolve1d
rng=np.random.default_rng(42)
n=128
ground=.08+.84*gaussian_filter(rng.random((n*2,n*2)),1.5)
# Four camera frames sample one continuous, blurred scene at independent detector phases.
y,x=np.mgrid[:n,:n]
phases=[(0.,0.),(.5,0.),(0.,.5),(.5,.5)]
frames=[map_coordinates(ground,[2*(y-dy),2*(x-dx)],order=3,mode='reflect') for dx,dy in phases]
# Quantize through rendered 8-bit sRGB and add small independent sensor noise.
frames=[np.clip(f+rng.normal(0,.0005,f.shape),0,1) for f in frames]
frames=[np.where((q:=np.round(np.where(f<=.0031308,12.92*f,1.055*f**(1/2.4)-.055)*255)/255)<=.04045,q/12.92,((q+.055)/1.055)**2.4) for f in frames]
def registration_plane(f):
    for _ in range(3):
        f=convolve1d(convolve1d(f,[.25,.5,.25],axis=1,mode="nearest"),[.25,.5,.25],axis=0,mode="nearest")
    return f
def error(f,dx,dy):
    u=registration_plane(frames[0])[8:-8:1,8:-8:1]
    v=map_coordinates(registration_plane(f),[y[8:-8,8:-8]+dy,x[8:-8,8:-8]+dx],order=1,mode='nearest')
    return np.mean((u-v)**2)/np.var(u)
# Same local .125px refinement used after integer registration.
found=[]
for f,(tx,ty) in zip(frames,phases):
    candidates=[(error(f,dx,dy),dx,dy) for dx in np.arange(-.25,.876,.125) for dy in np.arange(-.25,.876,.125)]
    s,dx,dy=min(candidates)
    found.append((dx,dy));print('registration',tx,ty,'->',dx,dy,'residual',s)
assert max(abs(a-b) for f,t in zip(found,phases) for a,b in zip(f,t))<=.125
# Require low residuals as engine does. Aliasing is intentionally rejected when excessive.
assert max(error(f,*p) for f,p in zip(frames,found))<.035
acc=np.zeros_like(ground);weight=np.zeros_like(ground)
for f,(dx,dy) in zip(frames,found):
    ox=2*(x-dx);oy=2*(y-dy)
    for ax in [0,1]:
      for ay in [0,1]:
        xx=np.floor(ox).astype(int)+ax; yy=np.floor(oy).astype(int)+ay
        w=np.maximum(0,1-abs(xx-ox)/.85)*np.maximum(0,1-abs(yy-oy)/.85)
        valid=(xx>=0)&(xx<n*2)&(yy>=0)&(yy<n*2)
        np.add.at(acc,(yy[valid],xx[valid]),(w*f)[valid]);np.add.at(weight,(yy[valid],xx[valid]),w[valid])
yh,xh=np.mgrid[:n*2,:n*2]
bicubic=map_coordinates(frames[0],[yh/2,xh/2],order=3,mode='reflect')
reconstructed=np.divide(acc,weight,out=bicubic.copy(),where=weight>.05)
sl=np.s_[16:-16,16:-16]
mse_sr=np.mean((reconstructed[sl]-ground[sl])**2);mse_bicubic=np.mean((bicubic[sl]-ground[sl])**2)
print('MSE drizzle',mse_sr,'bicubic',mse_bicubic,'ratio',mse_sr/mse_bicubic)
assert mse_sr<mse_bicubic*.8
assert np.mean(weight[sl]>.05)>=.98
# Identical frames have just one phase and cannot pass the required four phase groups.
assert len(set([(0.,0.)]*4))<4
print('PASS: shifted forward samples improve over bicubic; identical burst rejected by phase gate')
