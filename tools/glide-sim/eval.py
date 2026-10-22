exec(open('sim.py').read())
def human(word, rnd, aim=0.3, cut=0.35):
    pts=[]
    for p in ideal(word):
        pts.append((p[0]+rnd.gauss(0,aim*KW), p[1]+rnd.gauss(0,aim*RH*0.8)))
    if len(pts)==1: pts=[pts[0],(pts[0][0]+0.6,pts[0][1])]
    # corner cutting: replace inner points by bezier between neighbors
    path=[]
    for i in range(len(pts)-1):
        a,b=pts[i],pts[i+1]
        steps=max(4,int(d(a,b)*6))
        for s in range(steps):
            t=s/steps
            path.append((a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t))
    path.append(pts[-1])
    # smoothing passes ~ corner cutting
    k=int(cut*10)
    for _ in range(k):
        path=[path[0]]+[((path[i-1][0]+path[i][0]+path[i+1][0])/3,(path[i-1][1]+path[i][1]+path[i+1][1])/3) for i in range(1,len(path)-1)]+[path[-1]]
    # jitter
    path=[(x+rnd.gauss(0,0.04),y+rnd.gauss(0,0.04)) for x,y in path]
    return path

def decode_current(path, W_shape=9, W_miss=6, W_freq=0.9, n=40):
    user=resample(path,n); kw=KW; radius=1.6*kw
    sk=[c for c in pos if d(pos[c],path[0])<radius]; ek=[c for c in pos if d(pos[c],path[-1])<radius]
    pl=length(path); res=[]
    for s in sk:
        for e in ek:
            for w in byends.get(s+e,[]):
                if len(w)<2: continue
                pts=ideal(w); il=length(pts)
                if il>pl*2.2+kw*2 or il<pl*0.35-kw: continue
                idl=[pts[0]]*n if len(pts)==1 else resample(pts,n)
                shape=sum(d(user[k],idl[k]) for k in range(n))/n/kw
                miss=sum(max(0,min(d(p,k) for p in path)/kw-0.55) for k in pts)
                res.append((-shape*W_shape-miss*W_miss+base[w]*W_freq,w))
    res.sort(reverse=True)
    return [w for _,w in res[:5]]

WORDS="like the and you that what have this with just your know dont was are for not but can all get got love good see one out now time how about when going think want really right yeah well come back here there they them then did does like make take give tell call need feel last little long great thanks thank sorry happy today tomorrow tonight morning night home work school friend family mother father brother sister appropriate keyboard nothing because something someone everything people please maybe never always again still might should would could where which while their other these those first next after before every year week phone message doing okay hello later soon".split()
if __name__=="__main__":
    import random
    rnd=random.Random(7)
    tot=ok=0; fails=[]
    for w in WORDS:
        for t in range(4):
            r=decode_current(human(w,rnd))
            tot+=1; ok+= bool(r) and r[0]==w
            if not r or r[0]!=w: fails.append((w,r[:3]))
    print("top1",ok,"/",tot, round(ok/tot,3))
    for f in fails[:40]: print(f)
