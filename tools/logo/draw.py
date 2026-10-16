from PIL import Image, ImageDraw, ImageFont
import math

S = 4
N = 512 * S
BG = (16, 16, 16)
FG = (245, 245, 245)
MID = (158, 158, 158)
DIM = (90, 90, 90)


def canvas():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, N - 1, N - 1], radius=int(N * 0.22), fill=BG)
    return img, d


def P(x, y):
    return (x * N, y * N)


def poly(d, pts, color, w):
    pts = [P(*p) for p in pts]
    r = w * N / 2
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        steps = max(1, int(math.hypot(x1 - x0, y1 - y0) / 2))
        for k in range(steps + 1):
            x = x0 + (x1 - x0) * k / steps
            y = y0 + (y1 - y0) * k / steps
            d.ellipse([x - r, y - r, x + r, y + r], fill=color)


def logo_interlace():
    img, d = canvas()
    w = 0.085
    left = [(0.17, 0.30), (0.37, 0.72), (0.57, 0.30)]
    right = [(0.43, 0.30), (0.63, 0.72), (0.83, 0.30)]
    poly(d, left, MID, w)
    poly(d, [(0.47, 0.40), (0.63, 0.72)], BG, w + 0.05)
    poly(d, right, FG, w)
    return img


def logo_threads_play():
    img, d = canvas()
    tip = (0.80, 0.50)
    top, bottom = (0.26, 0.20), (0.26, 0.80)
    rows = 7
    for i in range(rows):
        y0 = top[1] + (bottom[1] - top[1]) * (i + 0.18) / rows
        y1 = top[1] + (bottom[1] - top[1]) * (i + 0.82) / rows
        def xr(y):
            return top[0] + (tip[0] - top[0]) * (1 - abs(y - 0.5) / 0.30)
        x0 = top[0] + (0.035 if i % 2 else 0)
        pts = [P(x0, y0), P(xr(y0), y0), P(xr(y1), y1), P(x0, y1)]
        d.polygon(pts, fill=FG if i % 2 == 0 else MID)
    return img


def bezier(p0, p1, p2, p3, n=200):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
                    u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]))
    return out


def logo_converge():
    img, d = canvas()
    w = 0.06
    join = (0.56, 0.50)
    for y, col in ((0.24, DIM), (0.76, MID), (0.50, FG)):
        pts = bezier((0.16, y), (0.36, y), (0.40, 0.50), join)
        poly(d, pts, col, w)
    tri = [P(0.52, 0.34), P(0.84, 0.50), P(0.52, 0.66)]
    d.polygon(tri, fill=FG)
    return img


if __name__ == "__main__":
    logos = [logo_interlace(), logo_threads_play(), logo_converge()]
    for i, im in enumerate(logos, 1):
        im.resize((512, 512), Image.LANCZOS).save(f"logo{i}.png")

    sheet = Image.new("RGB", (3 * 360 + 4 * 40, 360 + 120), (235, 235, 235))
    font = None
    for f in ["/system/fonts/Roboto-Bold.ttf", "/system/fonts/Roboto-Regular.ttf"]:
        try:
            font = ImageFont.truetype(f, 54)
            break
        except Exception:
            pass
    sd = ImageDraw.Draw(sheet)
    for i, im in enumerate(logos):
        x = 40 + i * 400
        small = im.resize((360, 360), Image.LANCZOS)
        sheet.paste(small, (x, 30), small)
        label = str(i + 1)
        tw = sd.textlength(label, font=font) if font else 20
        sd.text((x + 180 - tw / 2, 400), label, fill=(30, 30, 30), font=font)
    sheet.save("logos.png")
    print("ok")
