from draw import canvas, P, poly, bezier, N, BG, FG, MID, DIM, Image, ImageDraw, ImageFont
import math


def shield_pts(cx, top, w, h, n=120):
    left = cx - w / 2
    right = cx + w / 2
    shoulder = top + h * 0.08
    side_end = top + h * 0.48
    bottom = top + h
    pts = [(cx, top), (right, shoulder), (right, side_end)]
    pts += bezier((right, side_end), (right, top + h * 0.78), (cx + w * 0.18, top + h * 0.92), (cx, bottom), n)
    pts += bezier((cx, bottom), (cx - w * 0.18, top + h * 0.92), (left, top + h * 0.78), (left, side_end), n)
    pts += [(left, shoulder)]
    return [P(x, y) for x, y in pts]


def logo_shield():
    img, d = canvas()
    d.polygon(shield_pts(0.5, 0.15, 0.58, 0.72), fill=FG)
    d.polygon(shield_pts(0.5, 0.205, 0.46, 0.60), fill=BG)
    half = shield_pts(0.5, 0.205, 0.46, 0.60)
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).polygon(half, fill=255)
    ImageDraw.Draw(mask).rectangle([0, 0, N // 2, N], fill=0)
    shade = Image.new("RGBA", (N, N), (34, 34, 34, 255))
    img.paste(shade, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.polygon([P(0.42, 0.37), P(0.64, 0.495), P(0.42, 0.62)], fill=FG)
    return img


def logo_eye():
    img, d = canvas()
    upper = bezier((0.14, 0.52), (0.30, 0.24), (0.70, 0.24), (0.86, 0.52))
    lower = bezier((0.86, 0.52), (0.70, 0.78), (0.30, 0.78), (0.14, 0.52))
    d.polygon([P(*p) for p in upper + lower], fill=FG)
    inner_u = bezier((0.21, 0.52), (0.35, 0.32), (0.65, 0.32), (0.79, 0.52))
    inner_l = bezier((0.79, 0.52), (0.65, 0.70), (0.35, 0.70), (0.21, 0.52))
    d.polygon([P(*p) for p in inner_u + inner_l], fill=BG)
    d.polygon([P(0.44, 0.415), P(0.60, 0.515), P(0.44, 0.615)], fill=MID)
    poly(d, [(0.22, 0.80), (0.80, 0.22)], BG, 0.11)
    poly(d, [(0.22, 0.80), (0.80, 0.22)], FG, 0.055)
    return img


def logo_onion():
    img, d = canvas()
    cx, cy = 0.5, 0.5
    for r, col in ((0.34, DIM), (0.27, MID), (0.20, FG)):
        d.ellipse([P(cx - r, cy - r), P(cx + r, cy + r)], fill=col)
        g = r - 0.035
        d.ellipse([P(cx - g, cy - g), P(cx + g, cy + g)], fill=BG)
    d.polygon([P(0.445, 0.405), P(0.585, 0.50), P(0.445, 0.595)], fill=FG)
    return img


logos = [logo_shield(), logo_eye(), logo_onion()]
for i, im in enumerate(logos, 4):
    im.resize((512, 512), Image.LANCZOS).save(f"logo{i}.png")
sheet = Image.new("RGB", (3 * 360 + 4 * 40, 360 + 120), (235, 235, 235))
font = ImageFont.truetype("/system/fonts/Roboto-Regular.ttf", 54)
sd = ImageDraw.Draw(sheet)
for i, im in enumerate(logos):
    x = 40 + i * 400
    small = im.resize((360, 360), Image.LANCZOS)
    sheet.paste(small, (x, 30), small)
    label = str(i + 4)
    sd.text((x + 180 - sd.textlength(label, font=font) / 2, 400), label, fill=(30, 30, 30), font=font)
sheet.save("logos2.png")
print("ok")
