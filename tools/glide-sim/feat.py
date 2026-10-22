"""Precompute per-candidate features for many simulated swipes, so decoder weights
can be tuned quickly (the score is linear in the features)."""
import math, random, sys, pickle
from multiprocessing import Pool
from v3 import *

FEATS = ['dtw', 'miss', 'extra', 'se_lin', 'se_g', 'lenpen', 'anchor', 'corner', 'freq', 'anchor_n', 'wdtw']

def features(args):
    path, ts, n = args[0], args[1], 32
    user = resample(path, n)
    anc = anchors(path, ts)
    # weights for DTW: user points near anchors count more (Swype: turning points matter)
    sk = [c for c in pos if d(pos[c], path[0]) < 1.6]; ek = [c for c in pos if d(pos[c], path[-1]) < 1.6]
    pl = length(path); out = []
    for s in sk:
        for e in ek:
            for w in byends.get(s + e, []):
                if len(w) < 2: continue
                pts = ideal(w); il = length(pts)
                if il > pl * 2.2 + 2 or il < pl * 0.35 - 1: continue
                idl = [pts[0]] * n if len(pts) == 1 else resample(pts, n)
                INF = 1e9; band = 6
                prev = [INF] * (n + 1); prev[0] = 0
                for i in range(1, n + 1):
                    cur = [INF] * (n + 1)
                    lo = max(1, i - band); hi = min(n, i + band)
                    for j in range(lo, hi + 1):
                        c = d(user[i-1], idl[j-1])
                        cur[j] = c + min(prev[j], prev[j-1], cur[j-1])
                    prev = cur
                dtw = prev[n] / n
                miss = sum(max(0, min(d(p, k) for p in user) - 0.5) for k in pts)
                extra = sum(max(0, poly_dist(p, pts) - 0.5) for p in user) / n * 4
                ds, de = d(path[0], pts[0]), d(path[-1], pts[-1])
                lenpen = abs(math.log((il + 0.5) / (pl + 0.5)))
                ac = anchor_cost(anc, pts) if anc else 0.0
                tl = turn_letters(pts)
                corner = sum(max(0.0, min((d(t, a) for a in anc), default=1.0) - 0.6) for t in tl) if anc else 0.0
                # word letters that are turns but the path has no anchor near them, count
                out.append((w, [dtw, miss, extra, ds + de, (ds*ds + de*de), lenpen, ac, corner, base[w], len(anc), 0.0]))
    return args[2], out

def make(kind, seed, reps=2):
    rnd = random.Random(seed); jobs = []
    for w in E.WORDS:
        for _ in range(reps):
            if kind == 'normal': p, t = human_t(w, rnd, aim=0.3, cut=0.35)
            elif kind == 'sloppy': p, t = human_t(w, rnd, aim=0.42, cut=0.45, drift=0.25, sloppy_ends=0.45)
            elif kind == 'fast': p, t = human_t(w, rnd, aim=0.36, cut=0.6, dwell=(0, 10))
            jobs.append((p, t, w))
    return jobs

if __name__ == '__main__':
    kind, seed = sys.argv[1], int(sys.argv[2])
    jobs = make(kind, seed)
    with Pool(2) as pool:
        res = pool.map(features, jobs, chunksize=4)
    pickle.dump(res, open(f'feat_{kind}_{seed}.pkl', 'wb'))
    print('done', kind, seed, len(res))
