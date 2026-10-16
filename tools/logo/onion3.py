from PIL import Image, ImageDraw
import math
import os

import onion

N = onion.N
TOP = (89, 49, 107)
BOTTOM = (142, 140, 153)
PURPLE = onion.PURPLE
LIGHT = onion.LIGHT
WHITE = onion.WHITE


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


def play(img, cx, cy, size):
    d = ImageDraw.Draw(img)
    w = size * math.sqrt(3) / 2
    d.polygon([(cx - w / 3, cy - size / 2), (cx - w / 3, cy + size / 2), (cx + w * 2 / 3, cy)], fill=WHITE)


def shape(cx, cy, w, h):
    pts, tip = onion.bulb(cx, cy, w, h, N * 0.07)
    stem = [(cx - N * 0.035, pts[0][1] + N * 0.03), tip, (cx + N * 0.035, pts[0][1] + N * 0.03)]
    return pts, stem


CX, CY, W, H = N * 0.5, N * 0.56, N * 0.30, N * 0.30


def layered(img):
    d = ImageDraw.Draw(img)
    pts, stem = shape(CX, CY, W, H)
    d.polygon(pts, fill=PURPLE)
    d.polygon(stem, fill=PURPLE)
    for k, f in enumerate((0.72, 0.42)):
        lp, _ = onion.bulb(CX, CY + H * 0.02 * k, W * f, H * (0.96 - 0.02 * k), 0)
        d.line(lp, fill=LIGHT, width=int(N * 0.012), joint="curve")


def flat(img):
    d = ImageDraw.Draw(img)
    pts, stem = shape(CX, CY, W, H)
    d.polygon(pts, fill=PURPLE)
    d.polygon(stem, fill=PURPLE)


def outline(img):
    d = ImageDraw.Draw(img)
    pts, stem = shape(CX, CY, W, H)
    lw = int(N * 0.022)
    d.line(pts + [pts[0]], fill=LIGHT, width=lw, joint="curve")
    d.line([stem[0], stem[1], stem[2]], fill=LIGHT, width=lw, joint="curve")
    for f in (0.62,):
        lp, _ = onion.bulb(CX, CY, W * f, H * 0.96, 0)
        d.line(lp, fill=LIGHT, width=int(N * 0.014), joint="curve")


def make(kind):
    img = gradient()
    kind(img)
    play(img, CX + N * 0.015, CY + N * 0.01, N * 0.26)
    return img


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    sheet = Image.new("RGB", (3 * 560 + 40, 640), (240, 240, 244))
    d = ImageDraw.Draw(sheet)
    for i, kind in enumerate((layered, flat, outline)):
        im = make(kind).resize((512, 512), Image.LANCZOS)
        im.save(os.path.join(out, "orvid_onion%d.png" % (i + 1)))
        sheet.paste(im, (40 + i * 560, 40), im)
        d.text((40 + i * 560 + 250, 580), str(i + 1), fill=(40, 40, 40))
    sheet.save(os.path.join(out, "orvid_onions.png"))
    print("ok")
