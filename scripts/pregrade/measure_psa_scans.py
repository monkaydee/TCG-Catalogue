"""Measures centering and corner/edge wear on PSA's scans of graded cards (see DATASETS.md), for
train_grade_model.py. Images are <cert>_front.jpg / <cert>_back.jpg in IMAGE_DIR.

    python3 measure_psa_scans.py IMAGE_DIR metadata.csv out.json
"""
import csv, os, sys, json
import numpy as np
from PIL import Image
from concurrent.futures import ProcessPoolExecutor
import center as C, wear as Wr, edge_only as E

D, META, OUT = sys.argv[1:4]
meta = {r['filename'].split('_')[0]: r for r in csv.DictReader(open(META))}


def crop(p):
    """The card cut to the standard raster (the scans are cut close to the card already)."""
    a = C.load(p)
    h, w = a.shape[:2]
    e = {s: E.edge(a, s) for s in 'LRTB'}
    box = (e['L'], e['T'], w - e['R'], h - e['B'])
    im = Image.fromarray(a.astype('uint8')).crop(tuple(int(round(x)) for x in box)).resize((Wr.W, Wr.H), Image.BICUBIC)
    return np.asarray(im, np.float32)


def run(i):
    out = {'grade': meta.get(i, {}).get('grade', ''), 'name': meta.get(i, {}).get('card_name', '')}
    for side in ('front', 'back'):
        p = f'{D}/{i}_{side}.jpg'
        try:
            out[side] = Wr.wear(crop(p))
            c = C.centering(p)
            out[side + '_c'] = None if not c else (float(c['lr']), float(c['tb']))
        except Exception:
            out[side] = None
    return i, out


if __name__ == '__main__':
    ids = sorted(set(f.split('_')[0] for f in os.listdir(D) if f.endswith('_back.jpg')))
    with ProcessPoolExecutor(os.cpu_count()) as ex:
        res = dict(ex.map(run, ids, chunksize=8))
    json.dump(res, open(OUT, 'w'))
    print(len(res))
