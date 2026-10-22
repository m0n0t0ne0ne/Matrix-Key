"""Swype-style decoder experiments: timed swipes, pause/turn anchors, Gaussian ends."""
import math, random, sys
from v2 import *   # pos, base, byends, resample, length, ideal, poly_dist, decode_v2, d, KW, RH
import eval as E

# ---------------------------------------------------------------- timed swipes
def human_t(word, rnd, aim=0.3, cut=0.35, drift=0.0, sloppy_ends=0.0, dwell=(0, 45)):
    """Finger path with timestamps (ms). Minimum-jerk speed between letters: slow at
    letters, fast in between; optional pause at letters, lateral drift, sloppy start/end."""
    tg = []
    for p in ideal(word):
        tg.append((p[0] + rnd.gauss(0, aim * KW), p[1] + rnd.gauss(0, aim * RH * 0.8)))
    if len(tg) == 1: tg = [tg[0], (tg[0][0] + 0.6, tg[0][1])]
    if sloppy_ends:
        for idx in (0, -1):
            a = rnd.uniform(0, 2 * math.pi)
            tg[idx] = (tg[idx][0] + sloppy_ends * math.cos(a), tg[idx][1] + sloppy_ends * math.sin(a) * RH)
    pts, ts = [tg[0]], [0.0]
    t = 0.0
    for i in range(len(tg) - 1):
        a, b = tg[i], tg[i + 1]
        dist = d(a, b)
        dur = 60 + 75 * dist            # ms per segment
        n = max(3, int(dur / 8))         # 8 ms touch sampling
        nx, ny = -(b[1] - a[1]) / (dist or 1), (b[0] - a[0]) / (dist or 1)
        ph = rnd.uniform(0, 6.28)
        for k in range(1, n + 1):
            u = k / n
            s = 10 * u**3 - 15 * u**4 + 6 * u**5   # minimum jerk
            off = drift * math.sin(math.pi * u) * math.sin(ph + 3 * u)
            pts.append((a[0] + (b[0] - a[0]) * s + nx * off, a[1] + (b[1] - a[1]) * s + ny * off))
            ts.append(t + dur * u)
        t += dur
        if i < len(tg) - 2:
            pause = rnd.uniform(*dwell)
            t += pause
            pts.append(pts[-1]); ts.append(t)
    # corner cutting (geometry only)
    for _ in range(int(cut * 10)):
        pts = [pts[0]] + [((pts[i-1][0]+pts[i][0]+pts[i+1][0])/3, (pts[i-1][1]+pts[i][1]+pts[i+1][1])/3) for i in range(1, len(pts)-1)] + [pts[-1]]
    pts = [(x + rnd.gauss(0, 0.03), y + rnd.gauss(0, 0.03)) for x, y in pts]
    # the keyboard only records a point after the finger moved 2dp (~0.05 key)
    out, ot = [pts[0]], [ts[0]]
    for p, tt in zip(pts[1:], ts[1:]):
        if d(p, out[-1]) > 0.05: out.append(p); ot.append(tt)
    if out[-1] != pts[-1]: out.append(pts[-1]); ot.append(ts[-1])
    return out, ot

# ---------------------------------------------------------------- anchors
def anchors(path, ts):
    """Points where the finger turned sharply or slowed down: likely intended letters."""
    n = len(path)
    if n < 5: return []
    # speed (keys / 100 ms) over a small window
    sp = []
    for i in range(n):
        a, b = max(0, i - 2), min(n - 1, i + 2)
        dt = max(ts[b] - ts[a], 1)
        sp.append(length(path[a:b+1]) / dt * 100)
    med = sorted(sp)[n // 2] or 1e-6
    # turn angle using points ~0.35 key away on either side
    def back(i, r):
        j = i; acc = 0
        while j > 0 and acc < r: acc += d(path[j], path[j-1]); j -= 1
        return j
    def fwd(i, r):
        j = i; acc = 0
        while j < n - 1 and acc < r: acc += d(path[j], path[j+1]); j += 1
        return j
    k = [0.0] * n
    for i in range(1, n - 1):
        a, b = back(i, 0.35), fwd(i, 0.35)
        if a == i or b == i: continue
        v1 = (path[i][0]-path[a][0], path[i][1]-path[a][1]); v2 = (path[b][0]-path[i][0], path[b][1]-path[i][1])
        l1, l2 = math.hypot(*v1), math.hypot(*v2)
        if l1 < 1e-6 or l2 < 1e-6: continue
        cosang = max(-1, min(1, (v1[0]*v2[0]+v1[1]*v2[1]) / (l1*l2)))
        turn = math.acos(cosang) / math.pi           # 0 straight .. 1 reversal
        slow = max(0.0, 1 - sp[i] / (0.45 * med))     # well below typical speed
        k[i] = max(turn * 1.6, slow)
    out = []
    for i in range(2, n - 2):
        if k[i] >= 0.5 and k[i] == max(k[max(0, i-3):i+4]):
            if not out or d(path[out[-1]], path[i]) > 0.5: out.append(i)
            elif k[i] > k[out[-1]]: out[-1] = i
    # drop anchors right next to the start or end
    return [path[i] for i in out if d(path[i], path[0]) > 0.5 and d(path[i], path[-1]) > 0.5]

def anchor_cost(anc, pts):
    """Monotone alignment of anchors to the word's letters (letters may be skipped).
    Cost = sum of how far each anchor is from the letter it's matched to (beyond 0.45 key)."""
    if not anc: return 0.0
    m, L = len(anc), len(pts)
    INF = 1e9
    # dp[j] = best cost having matched anchors so far, last matched letter index <= j
    prev = [0.0] * L
    for a in anc:
        cur = [INF] * L
        best = INF
        for j in range(L):
            best = min(best, prev[j])
            c = max(0.0, d(a, pts[j]) - 0.45)
            cur[j] = best + c
        # prefix-min so "last letter <= j"
        for j in range(1, L): cur[j] = min(cur[j], cur[j-1])
        prev = cur
    return prev[-1]

def turn_letters(pts):
    out = []
    for i in range(1, len(pts) - 1):
        v1 = (pts[i][0]-pts[i-1][0], pts[i][1]-pts[i-1][1]); v2 = (pts[i+1][0]-pts[i][0], pts[i+1][1]-pts[i][1])
        l1, l2 = math.hypot(*v1), math.hypot(*v2)
        if l1 < 1e-6 or l2 < 1e-6: continue
        if (v1[0]*v2[0]+v1[1]*v2[1]) / (l1*l2) < 0.35: out.append(pts[i])   # turns > ~70°
    return out

def decode_v3(path, ts=None, Wd=6, Wm=5, Wx=4, Wf=0.6, Wlen=1.5, Wa=3.0, Wc=2.0, sig=0.55, Wse=1.0, n=32, ret_scores=False):
    user = resample(path, n); radius = 1.6
    anc = anchors(path, ts) if ts else []
    sk = [c for c in pos if d(pos[c], path[0]) < radius]; ek = [c for c in pos if d(pos[c], path[-1]) < radius]
    pl = length(path); res = []
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
                # Gaussian start/end: sloppy ends cost little, far ends a lot
                ds, de = d(path[0], pts[0]), d(path[-1], pts[-1])
                se = (ds * ds + de * de) / (2 * sig * sig)
                lenpen = abs(math.log((il + 0.5) / (pl + 0.5)))
                ac = anchor_cost(anc, pts) if anc else 0.0
                tl = turn_letters(pts)
                corner = sum(max(0.0, min((d(t, a) for a in anc), default=1.0) - 0.6) for t in tl) if anc else 0.0
                score = -dtw*Wd - miss*Wm - extra*Wx - se*Wse - lenpen*Wlen - ac*Wa - corner*Wc + base[w]*Wf
                res.append((score, w))
    res.sort(reverse=True)
    return res[:5] if ret_scores else [w for _, w in res[:5]]

def evaluate(fn, kind, seed=1, reps=2, words=None, **kw):
    rnd = random.Random(seed); tot = ok = 0; fails = []
    for w in (words or E.WORDS):
        for _ in range(reps):
            if kind == 'normal': p, t = human_t(w, rnd, aim=0.3, cut=0.35)
            elif kind == 'sloppy': p, t = human_t(w, rnd, aim=0.42, cut=0.45, drift=0.25, sloppy_ends=0.45)
            r = fn(p, t, **kw); tot += 1
            if r and r[0] == w: ok += 1
            else: fails.append((w, r[:3]))
    return ok / tot, fails

if __name__ == '__main__':
    import time
    which = sys.argv[1] if len(sys.argv) > 1 else 'both'
    v2 = lambda p, t, **k: decode_v2(p)
    for kind in ('normal', 'sloppy'):
        t0 = time.time()
        a2, _ = evaluate(v2, kind)
        a3, f3 = evaluate(decode_v3, kind)
        print(kind, 'v2', round(a2, 3), 'v3', round(a3, 3), f'{time.time()-t0:.0f}s', flush=True)
        print('  ', f3[:12], flush=True)
