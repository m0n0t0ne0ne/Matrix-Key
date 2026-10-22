from eval import *
def seg_dist(p,a,b):
    ax,ay=a; bx,by=b; dx,dy=bx-ax,by-ay; L=dx*dx+dy*dy
    if L==0: return d(p,a)
    t=max(0,min(1,((p[0]-ax)*dx+(p[1]-ay)*dy)/L))
    return d(p,(ax+t*dx,ay+t*dy))
def poly_dist(p,pts):
    if len(pts)==1: return d(p,pts[0])
    return min(seg_dist(p,pts[i],pts[i+1]) for i in range(len(pts)-1))
def decode_v2(path, Wd=6, Wm=5, Wx=4, Wse=2, Wf=0.6, Wlen=1.5, n=32):
    user=resample(path,n); radius=1.6
    sk=[c for c in pos if d(pos[c],path[0])<radius]; ek=[c for c in pos if d(pos[c],path[-1])<radius]
    pl=length(path); res=[]
    for s in sk:
        for e in ek:
            for w in byends.get(s+e,[]):
                if len(w)<2: continue
                pts=ideal(w); il=length(pts)
                if il>pl*2.2+2 or il<pl*0.35-1: continue
                idl=[pts[0]]*n if len(pts)==1 else resample(pts,n)
                # DTW with band
                INF=1e9; band=6
                prev=[INF]*(n+1); prev[0]=0
                for i in range(1,n+1):
                    cur=[INF]*(n+1)
                    lo=max(1,i-band); hi=min(n,i+band)
                    for j in range(lo,hi+1):
                        c=d(user[i-1],idl[j-1])
                        cur[j]=c+min(prev[j],prev[j-1],cur[j-1])
                    prev=cur
                dtw=prev[n]/n
                miss=sum(max(0,min(d(p,k) for p in user)-0.5) for k in pts)        # word letters not visited
                extra=sum(max(0,poly_dist(p,pts)-0.5) for p in user)/n*4            # path parts away from word
                se=d(path[0],pts[0])+d(path[-1],pts[-1])
                lenpen=abs(math.log((il+0.5)/(pl+0.5)))
                res.append((-dtw*Wd-miss*Wm-extra*Wx-se*Wse-lenpen*Wlen+base[w]*Wf,w))
    res.sort(reverse=True)
    return [w for _,w in res[:5]]
def run(fn, seed=7, reps=4, show=0, **kw):
    rnd=random.Random(seed); tot=ok=0; fails=[]
    for w in WORDS:
        for t in range(reps):
            r=fn(human(w,rnd),**kw); tot+=1; ok+= bool(r) and r[0]==w
            if not r or r[0]!=w: fails.append((w,r[:3]))
    return ok/tot, fails
if __name__=="__main__":
    a,f=run(decode_v2)
    print("v2",round(a,3))
    for x in f[:25]: print(x)
