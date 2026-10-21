# README images

The pictures in `docs/images/` are canvas drawings of Matrix Key made from the app's own
layout sizes, theme colors and icon paths (`KeyboardView`, `TopBarView`, `Icons`, `ThemeManager`,
the settings drawables). They are illustrations, not phone screenshots.

To redraw them after a design change: edit `render.html`, then run
`python3 tools/readme-images/shoot.py` (needs Playwright with Chromium).
