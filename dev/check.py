import re, sys
from PIL import Image, ImageStat

TMP = "/storage/emulated/0/Claude/tmp/dev_%s.%s"
RECT = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def lines(name):
    return open(TMP % (name, "txt"), encoding="utf-8").read().split("\n")


def rect(line):
    m = RECT.search(line)
    return tuple(map(int, m.groups())) if m else None


def find(name, pattern, index=0):
    hits = [l for l in lines(name) if pattern in l and rect(l)]
    return hits[index] if len(hits) > index else None


def text_of(line):
    m = re.search(r'"(.*)', line)
    return m.group(1).rstrip('"') if m else ""


def video(name):
    if find(name, "ImageView #poster"):
        return "poster(not started)"
    line = find(name, "TextureView #video") or find(name, "FrameLayout #stage")
    if not line:
        return "no-stage"
    l, t, r, b = rect(line)
    img = Image.open(TMP % (name, "png")).convert("L").crop((l, t + 60, r, b - 60))
    stat = ImageStat.Stat(img)
    return "frame" if stat.stddev[0] > 10 and stat.mean[0] > 15 else "black(mean=%.0f,sd=%.0f)" % (stat.mean[0], stat.stddev[0])


def main():
    cmd, name = sys.argv[1], sys.argv[2]
    if cmd == "video":
        print(video(name))
    elif cmd == "text":
        line = find(name, sys.argv[3])
        print(text_of(line) if line else "")
    elif cmd == "count":
        print(sum(1 for l in lines(name) if sys.argv[3] in l))
    elif cmd == "has":
        print("yes" if any(re.search(sys.argv[3], l) for l in lines(name)) else "no")
    elif cmd == "center":
        line = find(name, sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 0)
        if not line:
            sys.exit(1)
        l, t, r, b = rect(line)
        print((l + r) // 2, (t + b) // 2, t, b, l, r)
    elif cmd == "size":
        l, t, r, b = rect(lines(name)[0])
        print(r - l, b - t)


main()
