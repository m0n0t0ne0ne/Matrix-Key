import pickle, numpy as np
NAMES = ['dtw','miss','extra','se','se_short','lenpen','anchor','corner','freq','dir','loopfit','unexpl','missloop','hasdouble']
def load(name):
    res = pickle.load(open(name, 'rb')); out = []
    for target, cands in res:
        words = [w for w, _ in cands]
        X = np.array([f for _, f in cands], float) if cands else None
        out.append((words.index(target) if target in words else -1, X, target, words))
    return out
def acc(data, w, short_only=False):
    ok = tot = 0
    for idx, X, t, words in data:
        if short_only and len(set(t)) > 3 and not any(t[i]==t[i+1] for i in range(len(t)-1)): continue
        tot += 1
        if X is None or idx < 0: continue
        ok += int(np.argmax(X @ w) == idx)
    return ok / max(tot,1)
def vec(**k): return np.array([k.get(n, 0.0) for n in NAMES])
kinds = ['normal','sloppy','fast']
tr = {k: load(f'f4_{k}_1_all.pkl') for k in kinds}; te = {k: load(f'f4_{k}_2_all.pkl') for k in kinds}
nl = load('f4_normal_2_short_noloop.pkl')
def rep(name, w):
    print(name, ' '.join(f'{k}:{acc(te[k], w):.3f}(short {acc(te[k], w, True):.3f})' for k in kinds), 'noloop-short', f'{acc(nl, w):.3f}', flush=True)
V12 = vec(dtw=-6, miss=-1.25, extra=-2, se=-1.1, lenpen=-2.25, anchor=-2.25, corner=-1, freq=0.9)
rep('v1.2', V12)
def obj(w): return sum(acc(tr[k], w) for k in kinds) + sum(acc(tr[k], w, True) for k in kinds)
best = V12.copy(); best[NAMES.index('loopfit')] = 4; best[NAMES.index('unexpl')] = -2; best[NAMES.index('missloop')] = -1; best[NAMES.index('se_short')] = -1; best[NAMES.index('dir')] = -2
bo = obj(best)
for st in (0.5, 0.25, 0.1):
    imp = True
    while imp:
        imp = False
        for i in range(len(NAMES)):
            for m in (1+st, 1-st):
                w = best.copy()
                if w[i] == 0: w[i] = -0.5 if m > 1 else 0.5
                else: w[i] *= m
                o = obj(w)
                if o > bo + 1e-9: best, bo, imp = w, o, True
rep('tuned', best)
print(dict(zip(NAMES, np.round(best, 3))))
