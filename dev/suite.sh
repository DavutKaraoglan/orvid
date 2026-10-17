#!/data/data/com.termux/files/usr/bin/bash
# dev/suite.sh : runs several scenarios on the installed DEV build and writes Claude/tmp/suite_report.txt
cd "$(dirname "$0")/.."
D=dev/do.sh
C="python3 dev/check.py"
OUT=/storage/emulated/0/Claude/tmp/suite_report.txt
: > "$OUT"
say() { echo "$*" | tee -a "$OUT"; }
pass() { say "PASS $*"; }
fail() { say "FAIL $*"; }
check() { if [ "$2" = "$3" ]; then pass "$1"; else fail "$1 (got: $2)"; fi; }
errors='oynatılamadı|Bağlantı yok|needs an account|unavailable|HTTP [45]'
rm -f /storage/emulated/0/Claude/tmp/dev_s[0-9]_*
pgrep -f "[d]ev/serve.py" >/dev/null || (setsid python3 dev/serve.py >/dev/null 2>&1 &)
curl -s -X POST http://127.0.0.1:8471/clear
am start -n com.orvid/.ui.MainActivity >/dev/null 2>&1; sleep 3
dev/do.sh shot 00_alive >/dev/null || { echo "Orvid önde değil ya da cevap vermiyor; test durduruldu."; exit 2; }

home() { $D home; sleep 2; $D home; sleep 1.5; $D shot s0_home >/dev/null; }

player_checks() {
  local tag=$1 shot=$2
  title=$($C text $shot "TextView #name")
  [ -n "$title" ] && pass "$tag title: $title" || fail "$tag title empty"
  sub=$($C text $shot "TextView #sub")
  if echo "$sub $title" | command grep -Eq "$errors"; then fail "$tag error shown: $sub"; else pass "$tag no error text"; fi
  v=$($C video $shot); [ "$v" = frame ] && pass "$tag video frame" || fail "$tag video $v"
  rel=$($C count $shot "ThumbView #thumb")
  [ "$rel" -ge 2 ] && pass "$tag related $rel" || fail "$tag related $rel"
}

say "== S1 youtube: rick astley"
$D search "rick astley never gonna give you up"; sleep 16; $D shot s1_results >/dev/null
status=$($C text s1_results "TextView #dev_status"); say "   sources: $status"
echo "$status" | command grep -Eq "youtube [1-9]" && pass "S1 youtube returned results" || fail "S1 youtube results missing"
if read X Y T B L R < <($C center s1_results "src0 " 0); then
  $D tap $((L + 300)) $((T + 200)); sleep 15; $D shot s1_player >/dev/null
  player_checks S1 s1_player
  first=$($C text s1_player "TextView #name")
  read SX SY ST SB SL SR < <($C center s1_player "FrameLayout #stage")
  $D tap $SX $SY; sleep 1.2; $D click ctl_next; sleep 15; $D shot s1_next >/dev/null
  second=$($C text s1_next "TextView #name")
  [ -n "$second" ] && [ "$second" != "$first" ] && pass "S1 next opened: $second" || fail "S1 next ($second)"
  v=$($C video s1_next); [ "$v" = frame ] && pass "S1 next video frame" || fail "S1 next video $v"
  $D tap $SX $SY; sleep 1.2; $D click ctl_previous; sleep 12; $D shot s1_prev >/dev/null
  back=$($C text s1_prev "TextView #name")
  [ "$back" = "$first" ] && pass "S1 previous returned" || fail "S1 previous ($back)"
else
  fail "S1 no youtube card to open"
fi
home

say "== S2 other source: minecraft"
$D search "minecraft"; sleep 16; $D shot s2_results >/dev/null
say "   sources: $($C text s2_results 'TextView #dev_status')"
line=$(command grep -m1 -E '"#[0-9]+ src([1-9]|[1-9][0-9]) ' /storage/emulated/0/Claude/tmp/dev_s2_results.txt)
if [ -n "$line" ]; then
  read L T R B < <(echo "$line" | sed -E 's/.*\[(-?[0-9]+),(-?[0-9]+)\]\[(-?[0-9]+),(-?[0-9]+)\].*/\1 \2 \3 \4/')
  say "   opening: $(echo "$line" | sed -E 's/.*"(#[^"]*)/\1/' | cut -c1-40)"
  $D tap $((L + 300)) $((T + 200)); sleep 20; $D shot s2_player >/dev/null
  player_checks S2 s2_player
else
  fail "S2 no non-youtube card visible"
fi
home

say "== S3 live: lofi girl"
$D search "lofi hip hop radio beats to relax study"; sleep 16; $D shot s3_results >/dev/null
read X Y T B L R < <($C center s3_results "ThumbView #thumb" 0)
$D tap $X $Y; sleep 20; $D shot s3_player >/dev/null
v=$($C video s3_player); [ "$v" = frame ] && pass "S3 live video frame" || fail "S3 live video $v"
read SX SY ST SB SL SR < <($C center s3_player "FrameLayout #stage")
$D swipe $SX $((ST + 40)) $SX $((ST + 900)) 250; sleep 60; $D shot s3_waited >/dev/null
$D click mini; sleep 18; $D shot s3_reopened >/dev/null
sub=$($C text s3_reopened "TextView #sub")
if echo "$sub" | command grep -Eq "$errors"; then fail "S3 reopen error: $sub"; else pass "S3 reopen no error"; fi
v=$($C video s3_reopened); [ "$v" = frame ] && pass "S3 reopen video frame" || fail "S3 reopen video $v"

say "== S4 ui"
read SX SY ST SB SL SR < <($C center s3_reopened "FrameLayout #stage")
$D tap $SX $SY; sleep 1.2; $D click ctl_settings; sleep 1.5; $D shot s4_settings >/dev/null
[ "$($C count s4_settings 'Altyazı')" -ge 1 ] && pass "S4 settings has one subtitle row ($($C count s4_settings 'Altyazı'))" || fail "S4 settings subtitle row missing"
$D back; sleep 1.5
$D swipe $SX $((SB - 60)) $SX $((ST + 20)) 250; sleep 3; $D shot s4_fullscreen >/dev/null
read W H < <($C size s4_fullscreen); [ "$W" -gt "$H" ] && pass "S4 fullscreen landscape ${W}x${H}" || fail "S4 fullscreen not landscape ${W}x${H}"
read FX FY FT FB FL FR < <($C center s4_fullscreen "FrameLayout #stage")
$D swipe $FX $((FT + 100)) $FX $((FT + 700)) 250; sleep 3; $D shot s4_portrait >/dev/null
read W H < <($C size s4_portrait); [ "$H" -gt "$W" ] && pass "S4 back to portrait" || fail "S4 still landscape"
home
$D click btn_layout; sleep 1.5; $D shot s4_filters >/dev/null
[ "$($C has s4_filters 'Orvid sources')" = yes ] && pass "S4 filters show repository" || fail "S4 filters repository row missing"
$D home
say "== done: $(command grep -c '^PASS' $OUT) pass, $(command grep -c '^FAIL' $OUT) fail"
