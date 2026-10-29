# Reconstructed final JPEG marker probe; invoke after Java large mode.
from pathlib import Path
b=Path('/tmp/streaming-jpeg-probe/output.jpg').read_bytes();p=2;found=False
while p<len(b):
 assert b[p]==255
 tag=b[p+1];p+=2
 if tag==0xda:break
 size=int.from_bytes(b[p:p+2],'big');seg=b[p+2:p+size];p+=size
 if tag==0xc0:
  h=int.from_bytes(seg[1:3],'big');w=int.from_bytes(seg[3:5],'big');n=seg[5]
  assert(w,h,n,[seg[7+3*i] for i in range(n)])==(4000,12000,3,[17,17,17])
  print('Baseline SOF0 dimensions4000x12000 and 4:4:4 sampling PASS');found=True;break
assert found
assert b[-2:]==b'\xff\xd9'
print('JPEG EOI PASS')
