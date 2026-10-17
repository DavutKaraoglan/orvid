import re, sys
name, pattern, index = sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0
hits = []
for line in open("/storage/emulated/0/Claude/tmp/dev_%s.txt" % name, encoding="utf-8"):
    if pattern in line:
        m = re.search(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", line)
        if m:
            l, t, r, b = map(int, m.groups())
            hits.append(((l + r) // 2, (t + b) // 2, t, b))
if len(hits) <= index:
    sys.exit("not found: " + pattern)
x, y, t, b = hits[index]
print(x, y, t, b)
