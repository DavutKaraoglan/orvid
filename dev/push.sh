#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")/.."
DEV=1 ./build.sh | tail -1
pgrep -f "[d]ev/serve.py" >/dev/null || (setsid python3 dev/serve.py >/dev/null 2>&1 &)
: > dev/status.log
am start -n com.orvid/.dev.DevUpdate >/dev/null
for i in $(seq 1 60); do
  if command grep -q "^[0-9]* status" dev/status.log 2>/dev/null; then break; fi
  sleep 1
done
cat dev/status.log
