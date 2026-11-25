import pickle, math, random
import numpy as np
from sim import base
ranked = sorted(base, key=lambda w: -base[w])
rank = {w: i for i, w in enumerate(ranked)}
def history(scale):
    """A heavy user: common words typed many times (counts fall with rank)."""
    u = {w: int(scale * 400 / (r + 8)) for w, r in rank.items() if r < 1500}
    rnd = random.Random(3)
    habit = {w for w in ranked[:400] if rnd.random() < 0.2}
    return u, habit
KIND = ['normal', 'sloppy', 'fast']
data = {k: pickle.load(open(f'rec_{k}_1.pkl', 'rb')) for k in KIND}
W = dict(dtw=6.75, miss=2.81, extra=0.28, se=2.475, lenpen=1.69, anchor=4.45, corner=0.7)
def shape(f):  # dtw, miss, extra, se_lin, se_g, lenpen, ac, corner, base
    return -(f[0]*W['dtw'] + f[1]*W['miss'] + f[2]*W['extra'] + f[3]*W['se'] + f[5]*W['lenpen'] + f[6]*W['anchor'] + f[7]*W['corner'])
def run(u, habit, mode, cap=99, hab=4.5, gate=99, ufac=2.2):
    ok = tot = 0; fails = []
    for k in KIND:
        for target, cands in data[k]:
            tot += 1
            if not cands: fails.append((target, None)); continue
            sh = {w: shape(f) for w, f in cands}
            best = max(sh.values())
            sc = []
            for w, f in cands:
                if sh[w] < best - gate: continue
                ub = min(cap, ufac * math.log(1 + u.get(w, 0)))
                lang = (f[8] + ub) * 0.9 + (hab if w in habit else 0)
                sc.append((sh[w] + lang, w))
            sc.sort(reverse=True)
            if sc[0][1] == target: ok += 1
            else: fails.append((target, sc[0][1]))
    return ok / tot, fails
fresh = ({}, set())
if __name__ == "__main__":
  for scale in (0, 1, 3):
     h = history(scale) if scale else fresh
     print(f'--- history x{scale}')
     print(' current           ', round(run(*h, 'cur')[0], 3))
     for cap in (99, 3, 1.5):
         for hab in (4.5, 2.0, 1.0):
             for gate in (99, 6, 4, 3, 2):
                 a = run(*h, 'x', cap=cap, hab=hab, gate=gate)[0]
                 print(f' cap{cap} hab{hab} gate{gate}: {a:.3f}')
