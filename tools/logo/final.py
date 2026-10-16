from PIL import Image, ImageDraw
import math
import os

import onion
import onion4

N = onion.N
W = H = N * 0.30
CX = N * 0.5
CY = N * 0.5 + H * 0.125
LAYERS = ((1.0, onion4.DEEP), (0.80, onion4.PURPLE), (0.60, onion4.MID))


def glyph(img):
    d = ImageDraw.Draw(img)
    for i, (scale, color) in enumerate(LAYERS):
        dy = -H * 0.125 * (1 - scale)
        pts, tip = onion.bulb(CX, CY + dy, W * scale, H * scale, N * 0.07 * scale)
        d.polygon(pts, fill=color)
        if i == 0:
            d.polygon([(CX - N * 0.035, pts[0][1] + N * 0.03), tip, (CX + N * 0.035, pts[0][1] + N * 0.03)],
                      fill=color)
    d.polygon(onion4.triangle(N * 0.5, N * 0.5, N * 0.22), fill=onion4.WHITE)


def full():
    img = onion4.gradient()
    glyph(img)
    return img


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    full().resize((512, 512), Image.LANCZOS).save(os.path.join(out, "orvid_final.png"))
    print("ok")
