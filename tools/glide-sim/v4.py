"""v4: loops for double letters, short-word handling, twin offers."""
import math, random, sys, pickle
from v3 import *

SHORT = "of to in it is on at as be by do go he if me my no or so up us we am an oh ok hi off too see all will well feel good need been look book call still really little letter happy sorry coffee apple add odd egg bee free three tree week sleep soon food cool door keep seem less miss kiss pass".split()

def human_t2(word, rnd, loop_doubles=True, **kw):
    """Like human_t, but draws a small loop on doubled letters (Swype's convention)."""
    p, t = human_t(word, rnd, **kw)
    if not loop_doubles: return p, t
    dbl = [word[i] for i in range(len(word) - 1) if word[i] == word[i + 1]]
    for c in dbl:
        kx, ky = pos[c]
        # insert a loop at the path point closest to the key
        i = min(range(len(p)), key=lambda j: d(p[j], (kx, ky)))
        cx, cy = p[i]; r = 0.22 + rnd.uniform(-0.05, 0.08)
        loop = []; lt = []
        a0 = rnd.uniform(0, 6.28); dirn = rnd.choice((1, -1))
        for s in range(1, 17):
            a = a0 + dirn * 2 * math.pi * s / 16
            loop.append((cx + r * math.cos(a) - r * math.cos(a0), cy + r * math.sin(a) - r * math.sin(a0)))
            lt.append(t[i] + 12 * s)
        p = p[:i + 1] + loop + p[i + 1:]
        t = t[:i + 1] + lt + [x + 12 * 16 for x in t[i + 1:]]
    return p, t

def loops(path, kw=1.0):
    """Small loops: the swipe comes back across itself after 0.6-2.4 keys of travel,
    having turned most of a full circle on the way (Swype's double-letter gesture)."""
    step = 0.1 * kw
    rp = resample(path, max(8, int(length(path) / step) + 1))
    n = len(rp)
    heading = [math.atan2(rp[i+1][1] - rp[i][1], rp[i+1][0] - rp[i][0]) for i in range(n - 1)]
    out = []
    i = 0
    while i < n - 6:
        found = False
        for j in range(i + 6, min(n, i + int(2.4 * kw / step))):
            if d(rp[i], rp[j]) < 0.2 * kw:
                turn = 0.0; turn_abs = 0.0
                for k in range(i + 1, min(j, n - 1)):
                    dh = (heading[k] - heading[k-1] + math.pi) % (2 * math.pi) - math.pi
                    turn += dh; turn_abs += abs(dh)
                # a real loop turns steadily one way; a sharp V turns back and forth
                if abs(turn) > 1.4 * math.pi and abs(turn) > 0.8 * turn_abs and max(abs((heading[k] - heading[k-1] + math.pi) % (2 * math.pi) - math.pi) for k in range(i + 1, min(j, n - 1))) < 0.5 * math.pi:
                    seg = rp[i:j + 1]
                    c = (sum(x for x, _ in seg) / len(seg), sum(y for _, y in seg) / len(seg))
                    if not out or d(out[-1], c) > 0.6 * kw: out.append(c)
                    i = j; found = True
                    break
        i += 1
    return out

def doubles_at(w):
    return [w[i] for i in range(len(w) - 1) if w[i] == w[i + 1] and w[i] in pos]

def feat_v4(args):
    path, ts, target = args
    n = 32; user = resample(path, n); anc = anchors(path, ts); lp = loops(path)
    sk = [c for c in pos if d(pos[c], path[0]) < 1.6]; ek = [c for c in pos if d(pos[c], path[-1]) < 1.6]
    pl = length(path); out = []
    uvec = (path[-1][0] - path[0][0], path[-1][1] - path[0][1])
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
                short = 1.0 if len(pts) <= 2 else 0.0
                # direction mismatch for short (2-key) words
                ivec = (pts[-1][0] - pts[0][0], pts[-1][1] - pts[0][1])
                l1, l2 = math.hypot(*uvec), math.hypot(*ivec)
                dirc = 0.0
                if short and l1 > 1e-6 and l2 > 1e-6:
                    dirc = 1 - (uvec[0]*ivec[0] + uvec[1]*ivec[1]) / (l1 * l2)
                # loops: a loop on a doubled letter is a strong yes; a loop elsewhere a no
                dl = doubles_at(w); loopfit = 0.0
                for c in dl:
                    if any(d(L, pos[c]) < 0.85 for L in lp): loopfit += 1
                unexplained = sum(1 for L in lp if not any(d(L, pos[c]) < 0.85 for c in dl))
                missing_loop = len(dl) - loopfit   # doubled letter but no loop drawn
                out.append((w, [dtw, miss, extra, ds + de, (ds+de) * short, lenpen, ac, corner, base[w], dirc, loopfit, unexplained, missing_loop if lp else 0.0, 1.0 if dl else 0.0]))
    return target, out

def make(kind, seed, words, reps=2, loop_doubles=True):
    rnd = random.Random(seed); jobs = []
    for w in words:
        for _ in range(reps):
            kw = dict(normal=dict(aim=0.3, cut=0.35), sloppy=dict(aim=0.42, cut=0.45, drift=0.25, sloppy_ends=0.45), fast=dict(aim=0.36, cut=0.6, dwell=(0, 10)))[kind]
            p, t = human_t2(w, rnd, loop_doubles=loop_doubles, **kw)
            jobs.append((p, t, w))
    return jobs

if __name__ == '__main__':
    from multiprocessing import Pool
    kind, seed, which = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    MIXED = open('mixed_words.txt').read().split()
    words = SHORT if which == 'short' else E.WORDS + SHORT if which == 'all' else MIXED if which == 'mixed' else E.WORDS
    nl = which == 'short_noloop'
    if nl: words = SHORT
    jobs = make(kind, seed, words, loop_doubles=not nl)
    with Pool(2) as pool: res = pool.map(feat_v4, jobs, chunksize=4)
    pickle.dump(res, open(f'f4_{kind}_{seed}_{which}.pkl', 'wb'))
    print('done', kind, seed, which, len(res), flush=True)
