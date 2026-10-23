import numpy as np
exec(open('tune4.py').read().split("V12 = vec(")[0])
mtr = {k: load(f'f4_{k}_1_mixed.pkl') for k in ['normal','sloppy']}
mte = {k: load(f'f4_{k}_2_mixed.pkl') for k in ['normal','sloppy']}
def rep(name, w):
    print(name, 'common', ' '.join(f'{k}:{acc(te[k], w):.3f}' for k in kinds), '| short', ' '.join(f'{k}:{acc(te[k], w, True):.3f}' for k in kinds), '| rarer', ' '.join(f'{k}:{acc(mte[k], w):.3f}' for k in mte), flush=True)
V121 = vec(dtw=-3, miss=-1.875, extra=-0.75, se=-2.475, se_short=-0.5, lenpen=-1.125, anchor=-3.375, corner=-1, freq=0.9, dir=-2, loopfit=4, unexpl=-2, missloop=-0.5)
rep('v1.2.1', V121)
def obj(w): return sum(acc(tr[k], w) for k in kinds) + sum(acc(tr[k], w, True) for k in kinds) + 1.5*sum(acc(mtr[k], w) for k in mtr)
best = V121.copy(); bo = obj(best)
for st in (0.5, 0.25, 0.1):
    imp = True
    while imp:
        imp = False
        for i in range(len(NAMES)):
            for m in (1+st, 1-st):
                w = best.copy()
                if w[i] == 0: continue
                w[i] *= m
                o = obj(w)
                if o > bo + 1e-9: best, bo, imp = w, o, True
rep('tuned', best)
print(dict(zip(NAMES, np.round(best, 3))))
