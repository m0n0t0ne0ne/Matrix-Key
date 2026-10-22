import pickle, numpy as np, itertools, sys
NAMES = ['dtw', 'miss', 'extra', 'se_lin', 'se_g', 'lenpen', 'anchor', 'corner', 'freq']
def load(kind, seed):
    res = pickle.load(open(f'feat_{kind}_{seed}.pkl', 'rb'))
    out = []
    for target, cands in res:
        if not cands: out.append((None, None)); continue
        words = [w for w, _ in cands]
        X = np.array([f[:9] for _, f in cands], dtype=float)
        idx = words.index(target) if target in words else -1
        out.append((idx, X))
    return out
def acc(data, w):
    ok = 0
    for idx, X in data:
        if X is None or idx < 0: continue
        s = X @ w
        ok += int(np.argmax(s) == idx)
    return ok / len(data)
def vec(**k):
    return np.array([k.get(n, 0.0) for n in NAMES])
V2 = vec(dtw=-6, miss=-5, extra=-4, se_lin=-2, lenpen=-1.5, freq=0.6)
kinds = ['normal', 'sloppy', 'fast']
train = {k: load(k, 1) for k in kinds}; test = {k: load(k, 2) for k in kinds}
def report(name, w):
    print(name, ' '.join(f'{k}:{acc(train[k], w):.3f}/{acc(test[k], w):.3f}' for k in kinds), flush=True)
report('v2', V2)
def obj(w): return sum(acc(train[k], w) for k in kinds)
best = V2.copy(); bo = obj(best)
steps = [0.5, 0.25, 0.1]
for st in steps:
    improved = True
    while improved:
        improved = False
        for i in range(len(NAMES)):
            for mult in (1 + st, 1 - st, None):
                w = best.copy()
                if mult is None:
                    if w[i] != 0: continue
                    w[i] = -1.0 if NAMES[i] != 'freq' else 0.3
                else:
                    if w[i] == 0: continue
                    w[i] *= mult
                o = obj(w)
                if o > bo + 1e-9: best, bo, improved = w, o, True
report('tuned', best)
print(dict(zip(NAMES, np.round(best, 3))))
