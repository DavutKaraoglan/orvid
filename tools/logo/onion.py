from PIL import Image, ImageDraw, ImageFilter
import math
import os

S = 4
N = 512 * S
TOP = (89, 49, 107)
BOTTOM = (43, 42, 51)
PURPLE = (125, 70, 152)
LIGHT = (179, 127, 235)
SHADE = (100, 55, 125)
WHITE = (251, 251, 254)


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


def bulb(cx, cy, w, h, tip):
    pts = []
    for i in range(361):
        a = math.radians(i)
        x = math.sin(a)
        y = -math.cos(a)
        r = 1.0
        if y < 0:
            r = 1.0 - (abs(y) ** 3.2) * 0.62
        px = cx + x * w * r
        py = cy + y * h * (1.15 if y < 0 else 0.9)
        pts.append((px, py))
    top = min(pts, key=lambda p: p[1])
    return pts, (top[0], top[1] - tip)


def onion(img):
    d = ImageDraw.Draw(img)
    cx, cy = N * 0.5, N * 0.56
    w, h = N * 0.30, N * 0.30
    pts, tip = bulb(cx, cy, w, h, N * 0.07)
    d.polygon(pts, fill=PURPLE)
    stem = [(cx - N * 0.035, pts[0][1] + N * 0.03), (tip[0], tip[1]), (cx + N * 0.035, pts[0][1] + N * 0.03)]
    d.polygon(stem, fill=PURPLE)
    for k, f in enumerate((0.72, 0.42)):
        lp, _ = bulb(cx, cy + h * 0.02 * k, w * f, h * (0.96 - 0.02 * k), 0)
        d.line(lp, fill=LIGHT, width=int(N * 0.012), joint="curve")
    shade, _ = bulb(cx + w * 0.55, cy + h * 0.1, w * 0.35, h * 0.75, 0)
    for r in range(3):
        d.line([(cx - N * 0.05 + r * N * 0.05, cy + h * 0.9), (cx - N * 0.07 + r * N * 0.07, cy + h * 1.02)],
               fill=SHADE, width=int(N * 0.012))
    return cx, cy


def play(img, cx, cy, size):
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    w = size * math.sqrt(3) / 2
    d.polygon([(cx - w / 3, cy - size / 2), (cx - w / 3, cy + size / 2), (cx + w * 2 / 3, cy)], fill=WHITE + (255,))
    shadow = layer.split()[3].filter(ImageFilter.GaussianBlur(N * 0.012))
    dark = Image.new("RGBA", (N, N), (30, 10, 40, 110))
    img.paste(dark, (int(N * 0.006), int(N * 0.01)), shadow)
    img.alpha_composite(layer)


def logo():
    img = gradient()
    cx, cy = onion(img)
    play(img, cx + N * 0.015, cy + N * 0.01, N * 0.26)
    return img


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    im = logo().resize((512, 512), Image.LANCZOS)
    im.save(os.path.join(out, "orvid_onion.png"))
    print("ok")
