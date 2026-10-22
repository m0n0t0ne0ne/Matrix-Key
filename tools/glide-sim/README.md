# Glide-typing simulator

Python models of the swipe decoder used to test changes before they go into
`WordEngine.decodeGlide`. `v3.py` makes timed, human-like swipes (careful, fast
and sloppy) and holds the Swype-style decoder (turn/pause anchors). `feat.py`
precomputes each candidate's features and `tune.py` fits the weights on one set
of swipes and reports accuracy on a separate set.

Needs the word list at `en_50k.txt` (FrequencyWords, downloaded by the app build).
