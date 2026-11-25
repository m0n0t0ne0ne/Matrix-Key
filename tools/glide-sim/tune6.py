import pickle, math, numpy as np
from exp import history
NAMES = ['dtw','miss','extra','se_lin','se_g','lenpen','anchor','corner','freq']
def load(prefix, kinds=('normal','sloppy','fast'), seed=1):
    out = []
    for k in kinds:
        for target, cands in pickle.load(open(f'{prefix}_{k}_{seed}.pkl','rb')):
            if not cands: out.append(None); continue
            words = [w for w,_ in cands]
            X = np.array([f[:9] for _,f in cands], float)
            out.append((words.index(target) if target in words else -1, words, X))
    return out
HIST = {'fresh': ({}, set()), 'heavy': history(3)}
def prep(data, h):
    u, habit = h; res = []
    for it in data:
        if it is None: res.append(None); continue
        idx, words, X = it
        ub = np.array([math.log(1+u.get(w,0)) for w in words]); hb = np.array([w in habit for w in words], float)
        res.append((idx, X, ub, hb))
    return res
def acc(P, w, ufac, cap, hab):
    ok = n = 0
    for it in P:
        n += 1
        if it is None: continue
        idx, X, ub, hb = it
        if idx < 0: continue
        s = X @ w + np.minimum(ufac*ub, cap)*w[8] + hab*hb
        ok += int(np.argmax(s) == idx)
    return ok / n
train = {(p,h): prep(load(p), HIST[h]) for p in ('feat','rec') for h in HIST}
test = {h: prep(load('rec', seed=2), HIST[h]) for h in HIST}
CUR = dict(w=np.array([-6.75,-2.81,-0.28,-2.475,0,-1.69,-4.45,-0.7,0.9]), ufac=2.2, cap=99.0, hab=4.5)
def obj(p): return sum(acc(P, p['w'], p['ufac'], p['cap'], p['hab']) for P in train.values()) / len(train)
def rep(name, p):
    print(name, {k: round(acc(P, p['w'], p['ufac'], p['cap'], p['hab']),3) for k,P in train.items()},
          'test', {h: round(acc(P, p['w'], p['ufac'], p['cap'], p['hab']),3) for h,P in test.items()}, flush=True)
rep('current', CUR)
best = {k:(v.copy() if hasattr(v,'copy') else v) for k,v in CUR.items()}; bo = obj(best)
for st in (0.5, 0.25, 0.1):
    imp = True
    while imp:
        imp = False
        for key in list(range(9)) + ['ufac','cap','hab']:
            for mult in (1+st, 1-st, None):
                p = {k:(v.copy() if hasattr(v,'copy') else v) for k,v in best.items()}
                if isinstance(key, int):
                    if mult is None:
                        if p['w'][key] != 0: continue
                        p['w'][key] = -0.5
                    else: p['w'][key] *= mult
                else:
                    if mult is None: continue
                    p[key] = (p[key] if p[key] < 50 else 8.0) * mult
                o = obj(p)
                if o > bo + 1e-9: best, bo, imp = p, o, True
rep('tuned', best)
print(dict(zip(NAMES, np.round(best['w'],3))), 'ufac', round(best['ufac'],3), 'cap', round(best['cap'],3), 'hab', round(best['hab'],3))
