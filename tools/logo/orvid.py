from PIL import Image, ImageDraw
import math
import os

S = 4
N = 512 * S
BG = (28, 27, 34)
PURPLE = (125, 70, 152)
DEEP = (89, 49, 107)
LIGHT = (179, 127, 235)
WHITE = (251, 251, 254)


def canvas():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, N - 1, N - 1], radius=int(N * 0.22), fill=BG)
    return img, d


def ring(d, cx, cy, r, w, color):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=int(w))


def play(d, cx, cy, size, color):
    h = size
    w = size * math.sqrt(3) / 2
    pts = [(cx - w / 3, cy - h / 2), (cx - w / 3, cy + h / 2), (cx + w * 2 / 3, cy)]
    d.polygon(pts, fill=color)


def rounded_play(img, cx, cy, size, color, radius):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    play(ld, cx, cy, size, color + (255,))
    small = layer.resize((N // 8, N // 8), Image.LANCZOS)
    layer = small.resize((N, N), Image.LANCZOS)
    a = layer.split()[3].point(lambda v: 255 if v > 128 else 0)
    solid = Image.new("RGBA", img.size, color + (255,))
    img.paste(solid, (0, 0), a)


def logo_layers():
    img, d = canvas()
    c = N / 2
    ring(d, c, c, N * 0.36, N * 0.035, DEEP)
    ring(d, c, c, N * 0.28, N * 0.04, PURPLE)
    ring(d, c, c, N * 0.20, N * 0.045, LIGHT)
    rounded_play(img, c + N * 0.012, c, N * 0.17, WHITE, 0)
    return img


def logo_o_play():
    img, d = canvas()
    c = N / 2
    r = N * 0.27
    w = N * 0.11
    d.ellipse([c - r, c - r, c + r, c + r], outline=WHITE, width=int(w))
    gap = Image.new("L", img.size, 0)
    gd = ImageDraw.Draw(gap)
    play(gd, c + N * 0.20, c, N * 0.34, 255)
    img.paste(BG + (255,), (0, 0), gap)
    rounded_play(img, c + N * 0.20, c, N * 0.24, LIGHT, 0)
    return img


def logo_stack():
    img, d = canvas()
    c = N / 2
    rounded_play(img, c + N * 0.10, c, N * 0.46, DEEP, 0)
    rounded_play(img, c + N * 0.03, c, N * 0.40, PURPLE, 0)
    rounded_play(img, c - N * 0.04, c, N * 0.34, WHITE, 0)
    return img


def save_all(out):
    logos = [logo_layers(), logo_o_play(), logo_stack()]
    sheet = Image.new("RGB", (3 * 560 + 40, 640), (240, 240, 244))
    for i, im in enumerate(logos):
        small = im.resize((512, 512), Image.LANCZOS)
        small.save(os.path.join(out, "orvid_logo%d.png" % (i + 1)))
        sheet.paste(small, (40 + i * 560, 40), small)
        d = ImageDraw.Draw(sheet)
        d.text((40 + i * 560 + 240, 580), str(i + 1), fill=(40, 40, 40))
    sheet.save(os.path.join(out, "orvid_logos.png"))


if __name__ == "__main__":
    save_all(os.path.dirname(os.path.abspath(__file__)))
    print("ok")
