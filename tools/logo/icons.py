from draw import logo_converge, N, BG
from PIL import Image
import os

RES = os.path.expanduser("~/claude/orvid/res")
full = logo_converge()

glyph = full.copy()
px = glyph.load()
bg = BG + (255,)
data = [(0, 0, 0, 0) if p[:3] == BG else p for p in glyph.getdata()]
glyph.putdata(data)

for name, dp in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
    d = os.path.join(RES, "mipmap-" + name)
    os.makedirs(d, exist_ok=True)
    size = int(48 * dp)
    full.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
    fg_size = int(108 * dp)
    inner = int(fg_size * 0.80)
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    g = glyph.resize((inner, inner), Image.LANCZOS)
    off = (fg_size - inner) // 2
    fg.paste(g, (off, off), g)
    fg.save(os.path.join(d, "ic_launcher_foreground.png"))
print("ok")
