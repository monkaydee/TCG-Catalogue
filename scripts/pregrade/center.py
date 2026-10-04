"""Centering prototype v2: per-line scans from the outside in, robust medians."""
import numpy as np
from PIL import Image

def load(p, maxside=1400):
    im = Image.open(p).convert('RGB')
    s = maxside / max(im.size)
    if s < 1: im = im.resize((round(im.width*s), round(im.height*s)), Image.LANCZOS)
    return np.asarray(im, np.float32)

def _lines(a, side, frac=(0.2, 0.8), n=60):
    """Colour lines running inward from one side: array [n, length, 3]."""
    h, w = a.shape[:2]
    if side in 'LR':
        ys = np.linspace(h*frac[0], h*frac[1], n).astype(int)
        L = a[ys]                       # [n, w, 3]
        return L if side == 'L' else L[:, ::-1]
    xs = np.linspace(w*frac[0], w*frac[1], n).astype(int)
    L = a[:, xs].transpose(1, 0, 2)     # [n, h, 3]
    return L if side == 'T' else L[:, ::-1]

def _first_run(d, thr, start, run=3):
    above = d > thr
    for i in range(start, len(d)-run):
        if above[i:i+run].all(): return i
    return None

def edge_and_frame(a, side):
    """Outer edge and inner frame (pixels from that image side) from the median colour profile."""
    lines = _lines(a, side)
    length = lines.shape[1]
    prof = np.median(lines, axis=0)                       # [length, 3]
    grad = np.linalg.norm(np.diff(prof, axis=0), axis=1)  # [length-1]
    # The card's edge: the first colour step that is followed by an even stretch of at least 2 %
    # of the card (its border). A thin slab line or a short background strip doesn't qualify;
    # when the scan is cropped right at the card, the even stretch starts at the image side.
    region = int(0.13*length)
    need = max(10, int(0.02*length))
    def even(i):
        seg = prof[i:i+need]
        dev = np.linalg.norm(seg - np.median(seg, axis=0), axis=1)
        return np.percentile(dev, 80) < 12 and dev.max() < 40
    e = None
    for i in range(0, region):
        if (i <= 2 or np.linalg.norm(prof[i] - prof[i-3]) > 14) and even(i):
            e = i; break
    if e is None: return None
    skip = e + max(3, int(0.005*length))
    band = prof[skip:skip+max(4, int(0.008*length))]
    ref = np.median(band, axis=0)
    # Edge strength averaged over all lines: the printed frame is a straight line, so it adds up
    # across lines, while artwork edges (swirls, text) are at different places and average out.
    G = np.abs(np.diff(lines, axis=1)).sum(2).mean(0)          # [length-1]
    lo = skip + len(band)
    noise = np.median(G[skip:lo]) + 1e-3
    hi = min(len(G) - 1, e + int(0.2*length))
    f = None
    for i in range(lo, hi):
        if G[i] >= G[i-1] and G[i] >= G[i+1] and G[i] > max(3*noise, 10.0):
            # the colour must really change after the line (not a thin printed rule inside the border)
            after = np.median(prof[i+2:i+2+max(3, int(0.006*length))], axis=0)
            if np.linalg.norm(after - ref) > 12:
                f = i + 1; break
    if f is None: return None
    thr = 16.0
    # per-line positions, for the spread (how straight / sure the frame is)
    per = []
    for ln in lines:
        dl = np.linalg.norm(ln - ref, axis=1)
        k = _first_run(dl, thr, skip + len(band), 3)
        if k is not None and abs(k - f) < 0.05*length: per.append(k)
    spread = (np.percentile(per, 75) - np.percentile(per, 25)) if len(per) > 5 else 99
    return float(e), float(f), float(spread), len(per)/len(lines)

def centering(p):
    a = load(p)
    h, w = a.shape[:2]
    r = {s: edge_and_frame(a, s) for s in 'LRTB'}
    if any(v is None for v in r.values()): return None
    widths = {s: r[s][1] - r[s][0] for s in 'LRTB'}
    l, rr, t, b = widths['L'], widths['R'], widths['T'], widths['B']
    if min(l, rr, t, b) <= 0: return None
    return dict(raw=r, widths=(l, rr, t, b), lr=100*l/(l+rr), tb=100*t/(t+b),
                outer=(r['L'][0], w - r['R'][0], r['T'][0], h - r['B'][0]),
                inner=dict(L=r['L'][1], R=w - r['R'][1], T=r['T'][1], B=h - r['B'][1]),
                spread=max(r[s][2] for s in 'LRTB'), support=min(r[s][3] for s in 'LRTB'))

def worst(c):
    return max(c['lr'], 100-c['lr'], c['tb'], 100-c['tb'])
