"""Renders the README images from render.html (canvas drawings of the app).

Usage: python3 tools/readme-images/shoot.py   (needs: pip install playwright; playwright install chromium)
Writes PNGs to docs/images/.
"""
import asyncio, os, pathlib
from playwright.async_api import async_playwright

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent.parent / 'docs' / 'images'

async def main():
    html = (HERE / 'render.html').read_text().replace('__ICONS__', (HERE / 'icons.json').read_text())
    built = HERE / '_built.html'
    built.write_text(html)
    try:
        async with async_playwright() as p:
            b = await p.chromium.launch()
            pg = await b.new_page(viewport={'width': 3000, 'height': 2000})
            await pg.goto(built.as_uri())
            await pg.wait_for_function('window.done === true', timeout=20000)
            for cid in ['themes', 'toolbar', 'typing', 'tools', 'settings', 'numpad']:
                el = await pg.query_selector('#' + cid)
                if el:
                    await el.screenshot(path=str(OUT / f'{cid}.png'))
            await b.close()
    finally:
        os.remove(built)

asyncio.run(main())
