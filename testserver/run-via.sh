#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
PHASE="${1:-via}"
JAVA="D:/JAVA21/bin/java.exe"
NODE="D:/Node.js_24.21.0/node.exe"
SERVER_JAR=leaves-1.21.8.jar
rm -f server-log.txt
"$JAVA" -Xmx1024M -Xms512M -jar "$SERVER_JAR" --nogui > server-log.txt 2>&1 &
SRV=$!
echo "[run-via] pid=$SRV phase=$PHASE client=1.20.4"
ready=0
for i in $(seq 1 120); do
  if grep -q 'Done (' server-log.txt 2>/dev/null; then ready=1; break; fi
  if ! kill -0 "$SRV" 2>/dev/null; then echo "[run-via] server died early"; tail -30 server-log.txt; exit 1; fi
  sleep 2
done
if [ "$ready" != 1 ]; then echo "[run-via] not ready"; tail -30 server-log.txt; kill "$SRV" 2>/dev/null; exit 1; fi
echo "[run-via] ready"
SCC_OUT="capture-$PHASE.json" SCC_VERSION="1.20.4" "$NODE" ../testbot/bot-mf.js
BOT=$?
kill "$SRV" 2>/dev/null; sleep 3; kill -9 "$SRV" 2>/dev/null
echo "[run-via] === SCC + Via log lines ==="
grep -aE "SuperColourfulChat|ViaVersion|ViaBackwards|TestBot|gradient" server-log.txt | tail -30
exit $BOT