import numpy as np, center as C
def edge(a, side):
    """Card edge only: the even-border rule, else the first lasting step away from the background colour."""
    lines = C._lines(a, side); length = lines.shape[1]
    prof = np.median(lines, axis=0)
    region = int(0.13*length); need = max(10, int(0.02*length))
    def even(i):
        seg = prof[i:i+need]; dev = np.linalg.norm(seg - np.median(seg, axis=0), axis=1)
        return np.percentile(dev, 80) < 12 and dev.max() < 40
    for i in range(0, region):
        if (i <= 2 or np.linalg.norm(prof[i] - prof[i-3]) > 14) and even(i): return float(i)
    bg = np.median(prof[:3], axis=0)
    d = np.linalg.norm(prof - bg, axis=1)
    k = C._first_run(d, 30, 3, run=max(6, int(0.01*length)))
    return float(k) if k is not None and k < 0.02*length else 0.0
