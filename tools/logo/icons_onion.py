from PIL import Image
import os
import onion

RES = os.path.expanduser("~/claude/orvid/res")
N = onion.N

full = onion.logo()
glyph = Image.new("RGBA", (N, N), (0, 0, 0, 0))
cx, cy = onion.onion(glyph)
onion.play(glyph, cx + N * 0.015, cy + N * 0.01, N * 0.26)

for name, dp in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
    d = os.path.join(RES, "mipmap-" + name)
    os.makedirs(d, exist_ok=True)
    size = int(48 * dp)
    full.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
    fg_size = int(108 * dp)
    inner = int(fg_size * 0.78)
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    g = glyph.resize((inner, inner), Image.LANCZOS)
    off = (fg_size - inner) // 2
    fg.paste(g, (off, off), g)
    fg.save(os.path.join(d, "ic_launcher_foreground.png"))

with open(os.path.join(RES, "drawable", "launcher_gradient.xml"), "w") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="315"
        android:endColor="#FF2B2A33"
        android:startColor="#FF59316B"
        android:type="linear" />
</shape>
''')
with open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/launcher_gradient" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
''')
print("ok")
