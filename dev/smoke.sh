#!/data/data/com.termux/files/usr/bin/bash
# dev/smoke.sh [query] : drives the installed DEV build through a fixed scenario and saves dev_*.png/txt in Claude/tmp
set -e
cd "$(dirname "$0")/.."
D=dev/do.sh
Q=${1:-lofi}
step() { echo ">> $*"; }
at() { python3 dev/find.py "$@"; }
rm -f /storage/emulated/0/Claude/tmp/dev_[0-9][0-9]_*
curl -s -X POST http://127.0.0.1:8471/clear
am start -n com.orvid/.ui.MainActivity >/dev/null 2>&1; sleep 3
dev/do.sh shot 00_alive >/dev/null || { echo "Orvid önde değil ya da cevap vermiyor; test durduruldu."; exit 2; }
sleep 4
step home; $D shot 01_home
step search "$Q"; $D search "$Q"; sleep 15; $D shot 02_results
read LX LY LT LB < <(at 02_results "ListView #list" 0)
step scroll results; $D swipe $LX $((LB - 200)) $LX $((LT + 200)) 400; sleep 4; $D shot 03_scrolled
$D swipe $LX $((LT + 200)) $LX $((LB - 200)) 300; sleep 1; $D swipe $LX $((LT + 200)) $LX $((LB - 200)) 300; sleep 2
$D shot 04_top
read X Y T B < <(at 04_top "ThumbView #thumb" 0)
step preview; $D long $X $Y; sleep 8; $D shot 05_preview
step open first result; $D tap $X $Y; sleep 12; $D shot 06_player
read SX SY ST SB < <(at 06_player "FrameLayout #stage" 0)
read DX DY DT DB < <(at 06_player "ListView #details" 0)
step scroll related; $D swipe $DX $((DB - 150)) $DX $((DT + 150)) 400; sleep 3; $D shot 07_related
$D swipe $DX $((DT + 150)) $DX $((DB - 150)) 300; sleep 1; $D swipe $DX $((DT + 150)) $DX $((DB - 150)) 300; sleep 2
step controls; $D tap $SX $SY; sleep 1; $D shot 08_controls
step settings; $D click ctl_settings; sleep 1.5; $D shot 09_settings
$D back; sleep 1.5
step fullscreen; $D swipe $SX $((SB - 60)) $SX $((ST + 20)) 250; sleep 3; $D shot 10_fullscreen
read FX FY FT FB < <(at 10_fullscreen "FrameLayout #stage" 0)
step exit fullscreen; $D swipe $FX $((FT + 100)) $FX $((FT + 700)) 250; sleep 3; $D shot 11_portrait
read SX SY ST SB < <(at 11_portrait "FrameLayout #stage" 0)
step minimize; $D swipe $SX $((ST + 40)) $SX $((ST + 900)) 250; sleep 2.5; $D shot 12_minimized
step reopen; $D click mini; sleep 3; $D shot 13_reopened
$D home; sleep 2; $D home; sleep 1.5
step filters; $D click btn_layout; sleep 1.5; $D shot 14_filters
read PX PY PT PB < <(at 14_filters "ScrollView" 0)
$D swipe $PX $((PB - 100)) $PX $((PT + 100)) 400; sleep 1; $D swipe $PX $((PB - 100)) $PX $((PT + 100)) 400; sleep 1.5
$D shot 15_filters_scrolled
$D home
ls /storage/emulated/0/Claude/tmp/dev_[0-9][0-9]_*.png | wc -l
