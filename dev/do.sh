#!/data/data/com.termux/files/usr/bin/bash
# dev/do.sh shot NAME | tap X Y | long X Y | swipe X Y X2 Y2 [MS] | click ID | search TEXT | back | home
set -e
cd "$(dirname "$0")/.."
pgrep -f "[d]ev/serve.py" >/dev/null || (setsid python3 dev/serve.py >/dev/null 2>&1 &)
cmd=$1; shift
args=(--data-urlencode "cmd=$cmd")
case "$cmd" in
  shot) args+=(--data-urlencode "name=$1"); out=/storage/emulated/0/Claude/tmp/dev_$1.png; rm -f "$out" ;;
  tap|long) args+=(--data-urlencode "x=$1" --data-urlencode "y=$2") ;;
  swipe) args+=(--data-urlencode "x=$1" --data-urlencode "y=$2" --data-urlencode "x2=$3" --data-urlencode "y2=$4" --data-urlencode "ms=${5:-300}") ;;
  click) args+=(--data-urlencode "id=$1") ;;
  search) args+=(--data-urlencode "q=$*") ;;
esac
curl -s -X POST "${args[@]}" http://127.0.0.1:8471/push
if [ "$cmd" = shot ]; then
  for i in $(seq 1 40); do [ -f "$out" ] && break; sleep 0.25; done
  [ -f "$out" ] && echo "$out" || { echo "no shot"; tail -3 dev/status.log; exit 1; }
fi
