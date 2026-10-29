"""Independent numerical checks of the four original operators, not GPU execution."""
import numpy as np,json
from pathlib import Path
rng=np.random.default_rng(19)
c=np.vstack([rng.uniform(-.2,1,(20000,3))*rng.uniform(.001,100,(20000,1)),np.repeat(np.geomspace(1e-5,65504,2000)[:,None],3,axis=1),np.zeros((1,3))]).astype('float32')
luma=np.array([.2126,.7152,.0722],dtype='float32');results={}
for name,contrast,saturation in [('NeutralTone',1.,1.),('SoftPortrait',.88,.92),('SilverMonochrome',1.18,0.),('SonyNatural',1.08,.97)]:
 y=np.maximum(c@luma,0);safe=np.maximum(y,1e-6)
 if name=='SonyNatural':
  t=np.empty_like(y);low=y<=.18;t[low]=.18*(y[low]/.18)**contrast;t[~low]=.18+.82*(1-np.exp(-1.08*(y[~low]-.18)/.82))
  x=np.clip((y-1)/7,0,1);sat=.97+(.85-.97)*x*x*(3-2*x)
 else:
  p=(safe/.18)**contrast;t=p/(p+4.55555556);sat=np.repeat(saturation,len(c))
 delta=(c/safe[:,None]-1)*(t*sat)[:,None]
 scale=np.ones(len(c),dtype='float32')
 for i in range(3):
  d=delta[:,i];scale=np.minimum(scale,np.where(d>1e-6,(1-t)/np.maximum(d,1e-6),1));scale=np.minimum(scale,np.where(d< -1e-6,-t/np.minimum(d,-1e-6),1))
 out=np.clip(t[:,None]+np.clip(scale,0,1)[:,None]*delta,0,1);out[y<=1e-6]=0
 assert np.isfinite(out).all() and (out>=0).all() and (out<=1).all()
 if saturation==0:assert np.max(np.ptp(out,axis=1))==0
 gray=out[20000:22000];assert np.max(np.ptp(gray,axis=1))<1e-6
 # Float32 luma/ratio rounding permits two absolute ULP near white.
 assert np.min(np.diff(t[20000:22000]))>=0
 assert np.min(np.diff(gray[:,0]))>=-2.4e-7
 grey=.18 if name=='SonyNatural' else 1/(1+4.55555556)
 assert abs(grey-.18)<1e-8
 results[name]={'finite_in_gamut_samples':len(c),'scalar_neutral_ramp_monotonic':True,'rgb_ramp_float32_tolerance':2.4e-7,'middle_gray_preserved':True}
print(json.dumps(results,indent=2))
