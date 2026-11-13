#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
PHASE="${1:-cc}"
JAVA="D:/JAVA21/bin/java.exe"
NODE="D:/Node.js_24.21.0/node.exe"
SERVER_JAR=leaves-1.21.8.jar
rm -f server-log.txt
"$JAVA" -Xmx1024M -Xms512M -jar "$SERVER_JAR" --nogui > server-log.txt 2>&1 &
SRV=$!
echo "[run-cc] pid=$SRV phase=$PHASE"
ready=0
for i in $(seq 1 120); do
  if grep -q 'Done (' server-log.txt 2>/dev/null; then ready=1; break; fi
  if ! kill -0 "$SRV" 2>/dev/null; then echo "[run-cc] server died early"; tail -30 server-log.txt; exit 1; fi
  sleep 2
done
if [ "$ready" != 1 ]; then echo "[run-cc] not ready"; tail -30 server-log.txt; kill "$SRV" 2>/dev/null; exit 1; fi
echo "[run-cc] ready"
SCC_OUT="capture-$PHASE.json" SCC_TOGGLE="${SCC_TOGGLE:-/colorfulchat}" "$NODE" ../testbot/bot-cc.js
BOT=$?
kill "$SRV" 2>/dev/null; sleep 3; kill -9 "$SRV" 2>/dev/null
echo "[run-cc] === ColorfulChat log lines ==="
grep -aE "ColorfulChat|Using|TestBot" server-log.txt | tail -20
exit $BOT