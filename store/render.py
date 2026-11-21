#!/usr/bin/env python3
# Rendert de store-screenshots (recent/kiezen/contacten .html) naar 1080x1920 PNG (9:16).
# Nodig: playwright + pillow  (pip install playwright pillow ; playwright install chromium)
from playwright.sync_api import sync_playwright
from PIL import Image
import io, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
PAGES = {"02-kiezen":"kiezen.html","03-contacten":"contacten.html","06-recent":"recent.html"}
def render(html):
    with sync_playwright() as p:
        b=p.chromium.launch(); pg=b.new_page(viewport={"width":562,"height":1000},device_scale_factor=2)
        pg.goto("file://"+os.path.join(HERE,html),wait_until="networkidle"); pg.wait_for_timeout(1300)
        png=pg.screenshot(clip={"x":0,"y":0,"width":562,"height":1000}); b.close()
    return Image.open(io.BytesIO(png)).convert("RGB").resize((1080,1920),Image.LANCZOS)
if __name__=="__main__":
    out=HERE
    for name,html in PAGES.items():
        render(html).save(os.path.join(out,name+".png")); print("geschreven:",name+".png")
