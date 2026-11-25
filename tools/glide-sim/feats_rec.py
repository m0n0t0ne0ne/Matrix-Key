"""Features for swipes of the words in the owner's two Nov 24 recordings."""
import pickle, random, sys
from multiprocessing import Pool
import eval as E
from v3 import human_t
from feat import features
TEXT = """I'm creating a typing video to showcase the swiping and showing how the words I want are not necessarily
popping up instead i'm getting other words and then have to find the word I want in the suggestion The only words that
should pop up are the ones my finger lands on But it's probably challenging to code that in there needs to be some balance
that could be Technology is altering our deepest and most intimate experiences at a stunningly rapid pace What will happen
to marriage and parenthood when babies can be created by one parent or three or none What will happen to gender as medical
advances enable individuals to transition from one set of sexual characteristics to another or to remain happily"""
words = sorted({w.lower() for w in TEXT.split() if len(w) > 1 and w.lower() in E.base})
def make(kind, seed):
    rnd = random.Random(seed); jobs = []
    for w in words:
        for _ in range(2):
            if kind == 'normal': p, t = human_t(w, rnd, aim=0.3, cut=0.35)
            elif kind == 'sloppy': p, t = human_t(w, rnd, aim=0.42, cut=0.45, drift=0.25, sloppy_ends=0.45)
            else: p, t = human_t(w, rnd, aim=0.36, cut=0.6, dwell=(0, 10))
            jobs.append((p, t, w))
    return jobs
if __name__ == '__main__':
    kind, seed = sys.argv[1], int(sys.argv[2])
    with Pool(2) as pool: res = pool.map(features, make(kind, seed), chunksize=4)
    pickle.dump(res, open(f'rec_{kind}_{seed}.pkl', 'wb')); print(kind, seed, len(res), len(words))
