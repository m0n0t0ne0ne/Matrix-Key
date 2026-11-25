# Glide-typing simulator

Python models of the swipe decoder used to test changes before they go into
`WordEngine.decodeGlide`. `v3.py` makes timed, human-like swipes (careful, fast
and sloppy) and holds the Swype-style decoder (turn/pause anchors). `feat.py`
precomputes each candidate's features and `tune.py` fits the weights on one set
of swipes and reports accuracy on a separate set.

Needs the word list at `en_50k.txt` (FrequencyWords, downloaded by the app build).

`feats_rec.py` simulates swipes of the words from the owner's Nov 2026 test
recordings, `exp.py` models a heavy user's learned word counts, and `tune6.py`
tunes the weights together with the learned-word boost. That found that big
per-user word counts were overruling the shape of the swipe; version 1.9 scales
the shape weights by 1.25 and caps the learned boost (see `glideFreq`).
