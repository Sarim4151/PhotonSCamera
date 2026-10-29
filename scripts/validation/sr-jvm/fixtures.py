"""Forward sampled fixtures; verification consumes production Kotlin output, never a mirror."""
import sys,struct
from pathlib import Path
import numpy as np
from scipy.ndimage import gaussian_filter,map_coordinates
mode,folder=sys.argv[1:];folder=Path(folder)
def encode(f):return np.round(np.where(f<=.0031308,12.92*f,1.055*f**(1/2.4)-.055)*255).clip(0,255).astype(np.uint32)
def decode(f):
 q=(f&255)/255;return np.where(q<=.04045,q/12.92,((q+.055)/1.055)**2.4)
def save(name,f):
 p=encode(f);p=np.uint32(0xff000000)|(p<<16)|(p<<8)|p
 (folder/name).write_bytes(struct.pack('>ii',f.shape[1],f.shape[0])+p.astype('>u4').tobytes())
def load(name):
 b=(folder/name).read_bytes();w,h=struct.unpack('>ii',b[:8]);return decode(np.frombuffer(b[8:],dtype='>u4').reshape(h,w))
if mode=='generate':
 rng=np.random.default_rng(42)
 for name,w,h in [('static',128,128),('moving',1152,96),('native_motion',1152,96)]:
  truth=.08+.84*gaussian_filter(rng.random((h*2,w*2)),1.5)
  y,x=np.mgrid[:h,:w]
  for i,(dx,dy) in enumerate([(0,0)]*4 if name=='native_motion' else [(0,0),(.5,0),(0,.5),(.5,.5)]):
   f=map_coordinates(truth,[2*(y-dy),2*(x-dx)],order=3,mode='reflect')
   if name in ('moving','native_motion'):
    # Rectangle and its moving edge remain left of central registration crop x=64.
    f[(y>=24)&(y<70)&(x>=12+i*6)&(x<30+i*6)]=.95
   save(f'{name}_{i}.bin',f)
  np.save(folder/f'{name}_truth.npy',truth)
else:
 truth=np.load(folder/'static_truth.npy');out=load('static_output.bin');ref=load('static_0.bin')
 y,x=np.mgrid[:out.shape[0],:out.shape[1]]
 bic=map_coordinates(ref,[y/2,x/2],order=3,mode='reflect');sl=np.s_[16:-16,16:-16]
 a=np.mean((out[sl]-truth[sl])**2);b=np.mean((bic[sl]-truth[sl])**2)
 print(f'static linear MSE: production={a:.10g}, reference bicubic={b:.10g}, ratio={a/b:.6f}')
 assert a/b<.8
 out=load('moving_output.bin');ref=load('moving_0.bin');y,x=np.mgrid[:out.shape[0],:out.shape[1]]
 fallback=decode(encode(map_coordinates(ref,[y/2,x/2],order=1,mode='nearest')))
 # Union of moving-edge swept strips; fully inside vertical subject span.
 region=np.s_[56:132,58:94]
 delta=np.abs(out[region]-fallback[region]);count=np.count_nonzero(delta>1e-6)
 print(f'outside-window swept edge: {delta.size} pixels; fallback mismatches={count}; max linear delta={delta.max():.10g}')
 assert count==0
 out=load('native_motion_output.bin');ref=load('native_motion_0.bin')
 assert out.shape==ref.shape
 region=np.s_[28:66,29:47]
 assert np.array_equal(out[region],ref[region]), 'any-source native motion veto must preserve reference'
 print('PASS actual production Kotlin: 2 accepted four-phase fixtures; 1 identical-burst native fallback; 1 outside-window moving-edge fallback check')
