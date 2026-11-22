from pathlib import Path
import numpy as np
from PIL import Image, ImageOps
ROOT = Path(__file__).resolve().parents[2]
PUBLIC = Path(__file__).parent / 'public'

# Small, local render plates avoid external files and keep the Remotion project portable.
plates = {
    'ocean.jpg': ROOT/'brand/motion/atlantic-night-sea.png',
    'map.jpg': ROOT/'brand/episode-01-visuals/atlantic-chart.png',
    'deck.jpg': ROOT/'brand/episode-01-visuals/empty-deck.png',
    'documents.jpg': ROOT/'brand/episode-01-visuals/inquiry-documents.png',
}
for name, src in plates.items():
    im=Image.open(src).convert('RGB')
    im=ImageOps.fit(im,(1280,720),method=Image.Resampling.LANCZOS,centering=(.5,.5))
    im.save(PUBLIC/name,'JPEG',quality=93,optimize=True,progressive=True)

source=ROOT/'brand/motion/brigantine-key.png'; out=PUBLIC/'ship.png'
im=Image.open(source).convert('RGB'); a=np.asarray(im).astype(np.float32)
r,g,b=a[:,:,0],a[:,:,1],a[:,:,2]; dom=g-np.maximum(r,b)
alpha=np.clip((68-dom)/34,0,1)
# Despill green reflections and matte edges without softening the rigging.
spill=np.clip((dom-12)/95,0,1)
a[:,:,1]=a[:,:,1]*(1-spill)+np.maximum(r,b)*spill
rgba=np.dstack((np.clip(a,0,255),alpha*255)).astype(np.uint8)
Image.fromarray(rgba,'RGBA').save(out,optimize=True)
print('Prepared Remotion plates:')
for p in sorted(PUBLIC.glob('*')):
    if p.is_file(): print(f'  {p.name}: {p.stat().st_size:,} bytes')
