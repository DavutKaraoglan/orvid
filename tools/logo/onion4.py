from PIL import Image, ImageDraw
import math
import os

import onion

N = onion.N
TOP = (170, 120, 225)
BOTTOM = (248, 246, 252)
DEEP = (98, 46, 150)
PURPLE = (125, 70, 190)
MID = (160, 110, 222)
SOFT = (200, 165, 240)
WHITE = (255, 255, 255)
CX, CY, W, H = N * 0.5, N * 0.56, N * 0.30, N * 0.30


def gradient():
    img = Image.new("RGBA", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(0, N, 4):
            t = (x + y) / (2 * N)
            c = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)) + (255,)
            for k in range(4):
                px[x + k, y] = c
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], radius=int(N * 0.22), fill=255)
    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def triangle(cx, cy, size):
    w = size * math.sqrt(3) / 2
    return [(cx - w / 3, cy - size / 2), (cx - w / 3, cy + size / 2), (cx + w * 2 / 3, cy)]


def bulb_fill(d, scale, color, dy=0.0):
    pts, tip = onion.bulb(CX, CY + dy, W * scale, H * scale, N * 0.07 * scale)
    d.polygon(pts, fill=color)
    return pts, tip


def stem(d, pts, tip, color):
    d.polygon([(CX - N * 0.035, pts[0][1] + N * 0.03), tip, (CX + N * 0.035, pts[0][1] + N * 0.03)], fill=color)


def whole(img):
    d = ImageDraw.Draw(img)
    pts, tip = bulb_fill(d, 1.0, PURPLE)
    stem(d, pts, tip, PURPLE)
    d.polygon(triangle(CX + N * 0.015, CY + N * 0.01, N * 0.26), fill=WHITE)


def split_play(img):
    d = ImageDraw.Draw(img)
    pts, tip = bulb_fill(d, 1.0, DEEP)
    stem(d, pts, tip, DEEP)
    tri = triangle(CX + N * 0.015, CY + N * 0.01, N * 0.30)
    d.polygon(tri, fill=SOFT)
    top_half = [tri[0], (tri[0][0], CY + N * 0.01), tri[2]]
    d.polygon(top_half, fill=WHITE)


def layered(img):
    d = ImageDraw.Draw(img)
    pts, tip = bulb_fill(d, 1.0, DEEP)
    stem(d, pts, tip, DEEP)
    bulb_fill(d, 0.80, PURPLE, N * 0.03)
    bulb_fill(d, 0.60, MID, N * 0.06)
    d.polygon(triangle(CX + N * 0.015, CY + N * 0.06, N * 0.22), fill=WHITE)


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    sheet = Image.new("RGB", (3 * 560 + 40, 640), (60, 60, 66))
    dd = ImageDraw.Draw(sheet)
    for i, kind in enumerate((whole, split_play, layered)):
        img = gradient()
        kind(img)
        im = img.resize((512, 512), Image.LANCZOS)
        im.save(os.path.join(out, "orvid_soft%d.png" % (i + 1)))
        sheet.paste(im, (40 + i * 560, 40), im)
        dd.text((40 + i * 560 + 250, 580), str(i + 1), fill=(230, 230, 230))
    sheet.save(os.path.join(out, "orvid_soft.png"))
    print("ok")
