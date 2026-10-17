#!/data/data/com.termux/files/usr/bin/bash
# dev/grid.sh : 3-column grid + long-press preview prefetch scenario; appends to Claude/tmp/grid_report.txt
cd "$(dirname "$0")/.."
D=dev/do.sh
C="python3 dev/check.py"
T=/storage/emulated/0/Claude/tmp
OUT=$T/grid_report.txt
: > "$OUT"
say() { echo "$*" | tee -a "$OUT"; }
pass() { say "PASS $*"; }
fail() { say "FAIL $*"; }
tapText() { read X Y _ < <($C center "$1" "\"$2" 0) && $D tap $X $Y; }
rm -f $T/dev_g[0-9]_*
pgrep -f "[d]ev/serve.py" >/dev/null || (setsid python3 dev/serve.py >/dev/null 2>&1 &)
curl -s -X POST http://127.0.0.1:8471/clear
am start -n com.orvid/.ui.MainActivity >/dev/null 2>&1; sleep 3
dev/do.sh shot 00_alive >/dev/null || { echo "Orvid önde değil ya da cevap vermiyor; test durduruldu."; exit 2; }
$D home; sleep 2; $D home; sleep 1.5

say "== G1 switch to 3-column grid"
$D click btn_layout; sleep 1.5; $D shot g1_filters >/dev/null
tapText g1_filters "Üç sütun ızgara"; sleep 0.8
tapText g1_filters "Kaydet"; sleep 1.5
$D search "minecraft"; sleep 16; $D shot g1_grid >/dev/null
row=$(python3 - <<'P'
import re
tops={}
for l in open("/storage/emulated/0/Claude/tmp/dev_g1_grid.txt",encoding="utf-8"):
    if "ThumbView #thumb" in l:
        m=re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",l)
        if m: tops.setdefault(m.group(2),0); tops[m.group(2)]+=1
print(max(tops.values()) if tops else 0)
P
)
[ "$row" = 3 ] && pass "G1 three cards per row" || fail "G1 cards per row = $row"
loaded=$(command grep -c "thumb [0-9]*KB" $T/dev_g1_grid.txt); cells=$($C count g1_grid "ThumbView #thumb")
say "   thumbs loaded $loaded / $cells cells"
[ "$loaded" -ge $((cells / 2)) ] && pass "G1 thumbnails loading" || fail "G1 thumbnails loaded $loaded/$cells"
read LX LY LT LB _ < <($C center g1_grid "ListView #list")
$D swipe $LX $((LB - 200)) $LX $((LT + 300)) 400; sleep 5; $D shot g1_scrolled >/dev/null
row2=$($C count g1_scrolled "ThumbView #thumb")
[ "$row2" -ge 6 ] && pass "G1 scrolled grid has $row2 cells" || fail "G1 scrolled grid $row2 cells"
$D swipe $LX $((LT + 300)) $LX $((LB - 200)) 300; sleep 1; $D swipe $LX $((LT + 300)) $LX $((LB - 200)) 300; sleep 2
$D shot g1_top >/dev/null

say "== G2 long-press preview in grid"
read X Y T0 B0 L0 R0 < <($C center g1_top "ThumbView #thumb" 1)
$D long $X $Y; sleep 7; $D shot g2_preview >/dev/null
python3 - "$L0" "$T0" "$R0" "$B0" <<'P' | tee -a /storage/emulated/0/Claude/tmp/grid_report.txt
import re, sys
from PIL import Image, ImageStat
l,t,r,b = map(int, sys.argv[1:])
tex = None
for line in open("/storage/emulated/0/Claude/tmp/dev_g2_preview.txt", encoding="utf-8"):
    if "TextureView" in line:
        m = re.search(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", line)
        if m:
            x1,y1,x2,y2 = map(int, m.groups())
            if x1 >= l-2 and x2 <= r+2 and y1 >= t-2 and y2 <= b+2: tex = (x1,y1,x2,y2)
if not tex:
    print("FAIL G2 no preview surface inside the cell")
else:
    a = Image.open("/storage/emulated/0/Claude/tmp/dev_g2_preview.png").convert("L").crop(tex)
    s = ImageStat.Stat(a)
    print(("PASS" if s.stddev[0] > 10 else "FAIL") + " G2 preview playing in cell (sd=%.0f)" % s.stddev[0])
P
sleep 15; $D shot g2_warm >/dev/null
prep=$(command grep -A1 -m2 "\"#2 " $T/dev_g2_warm.txt | command grep -o "prep [^ ]* [^ ]*" | head -1)
say "   preview card prep: $prep"
echo "$prep" | command grep -q "warm [1-9]" && pass "G2 preview prefetch warmed" || fail "G2 preview prefetch: $prep"

say "== G3 open the previewed video"
$D tap $X $Y
for s in 1.5 3 6; do sleep $( [ $s = 1.5 ] && echo 1.5 || ([ $s = 3 ] && echo 1.5 || echo 3) ); $D shot g3_open_$s >/dev/null; say "   after ${s}s: $($C video g3_open_$s)"; done
v=$($C video g3_open_6); [ "$v" = frame ] && pass "G3 video playing within 6s" || fail "G3 not playing after 6s ($v)"

say "== G4 restore large cards"
$D home; sleep 2; $D home; sleep 1.5
$D click btn_layout; sleep 1.5; $D shot g4_filters >/dev/null
tapText g4_filters "Büyük kart"; sleep 0.8; tapText g4_filters "Kaydet"; sleep 1
say "== done: $(command grep -c '^PASS' $OUT) pass, $(command grep -c '^FAIL' $OUT) fail"
