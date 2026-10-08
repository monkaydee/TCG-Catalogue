"""Corner and edge wear on a card cut to the standard raster: how much the outermost strip differs
from the border colour just inside it (whitening, chips, dings)."""
import numpy as np
W, H = 945, 1320          # 15 px per mm
R = int(3.2*15)           # corner radius of a standard card, px

def lum(a): return a @ np.array([0.299, 0.587, 0.114], np.float32)

def wear(a):
    """Features: per edge (T,R,B,L) and corner (TL,TR,BR,BL) the share of defect pixels, and how
    strongly they stand out."""
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    # distance to the cut (rounded rectangle)
    cx = np.clip(xx, R, w-1-R); cy = np.clip(yy, R, h-1-R)
    corner_d = R - np.hypot(xx-cx, yy-cy)          # >0 inside near corners
    straight_d = np.minimum.reduce([xx, w-1-xx, yy, h-1-yy]).astype(np.float32)
    in_corner_zone = (np.abs(xx-cx) > 0) & (np.abs(yy-cy) > 0)
    dist = np.where(in_corner_zone, corner_d, straight_d)   # distance inside the cut, px
    inside = dist >= 0
    ring = inside & (dist < 9)                       # outermost ~0.6 mm
    ref_band = (dist >= 14) & (dist < 30)            # ~1-2 mm in: border colour
    L = lum(a)
    feats = {}
    zones = {
        'T': (yy < h*0.5) & (xx > w*0.08) & (xx < w*0.92) & (yy < 40),
        'B': (yy > h*0.5) & (xx > w*0.08) & (xx < w*0.92) & (yy > h-41),
        'L': (xx < w*0.5) & (yy > h*0.06) & (yy < h*0.94) & (xx < 40),
        'R': (xx > w*0.5) & (yy > h*0.06) & (yy < h*0.94) & (xx > w-41),
        'TL': (xx < w*0.08) & (yy < h*0.06), 'TR': (xx > w*0.92) & (yy < h*0.06),
        'BR': (xx > w*0.92) & (yy > h*0.94), 'BL': (xx < w*0.08) & (yy > h*0.94),
    }
    for k, z in zones.items():
        refm = ref_band & z
        if refm.sum() < 20: feats[k] = (0.0, 0.0, 0.0); continue
        ref = np.median(a[refm], axis=0); refL = np.median(L[refm])
        spread = np.percentile(np.linalg.norm(a[refm]-ref, axis=1), 80)
        rm = ring & z
        d = np.linalg.norm(a[rm]-ref, axis=1)
        thr = max(40.0, 2.5*spread)
        bad = d > thr
        whiter = bad & (L[rm] - refL > 30)
        feats[k] = (float(bad.mean()), float(whiter.mean()), float(np.percentile(d, 95)/(thr)))
    return feats
