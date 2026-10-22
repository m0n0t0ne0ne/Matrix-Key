import math, random, sys
KW=1.0; RH=1.38   # row pitch relative to key width (54dp rows / ~39dp keys)
rows=["qwertyuiop","asdfghjkl","zxcvbnm"]; offs=[0,0.5,1.5]
pos={c:((i+o+0.5)*KW,(r+0.5)*RH) for r,(s,o) in enumerate(zip(rows,offs)) for i,c in enumerate(s)}
base={}
for line in open('en_50k.txt'):
    w,_,n=line.strip().partition(' ')
    if not w or not all(ch.isalpha() or ch=="'" for ch in w): continue
    if w[0]=="'" or w[-1]=="'": continue
    if len(w)==1 and w not in('a','i'): continue
    base.setdefault(w, math.log(int(n or 1)+1))
byends={}
for w in base:
    l=[c for c in w if c.isalpha()]
    if l and all(c in pos for c in l): byends.setdefault(l[0]+l[-1],[]).append(w)
d=math.dist
def length(p): return sum(d(p[i-1],p[i]) for i in range(1,len(p)))
def resample(src,n):
    tot=length(src)
    if tot<=0: return [src[0]]*n
    step=tot/(n-1); out=[src[0]]; acc=0; i=1; prev=src[0]
    while i<len(src) and len(out)<n:
        cur=src[i]; dd=d(prev,cur)
        if acc+dd>=step and dd>0:
            t=(step-acc)/dd; q=(prev[0]+t*(cur[0]-prev[0]),prev[1]+t*(cur[1]-prev[1])); out.append(q); prev=q; acc=0
        else: acc+=dd; prev=cur; i+=1
    while len(out)<n: out.append(src[-1])
    return out
def ideal(w):
    out=[];last=None
    for c in w:
        if c==last or c=="'": continue
        out.append(pos[c]); last=c
    return out
