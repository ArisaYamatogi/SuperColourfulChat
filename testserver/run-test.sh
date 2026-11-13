#!/usr/bin/env bash
# usage: bash run-test.sh <phase-name>
set -u
cd "$(dirname "$0")"
PHASE="${1:-test}"
JAVA="D:/JAVA21/bin/java.exe"
NODE="D:/Node.js_24.21.0/node.exe"
if [ -s leaves-1.21.8.jar ]; then SERVER_JAR=leaves-1.21.8.jar; else SERVER_JAR=paper-1.21.8-60.jar; fi
echo "[run] using $SERVER_JAR"

rm -f server-log.txt
"$JAVA" -Xmx1024M -Xms512M -jar "$SERVER_JAR" --nogui > server-log.txt 2>&1 &
SRV=$!
echo "[run] server pid=$SRV phase=$PHASE"

ready=0
for i in $(seq 1 120); do
    if grep -q 'Done (' server-log.txt 2>/dev/null; then ready=1; break; fi
    if ! kill -0 "$SRV" 2>/dev/null; then echo "[run] server died early"; tail -30 server-log.txt; exit 1; fi
    sleep 2
done
if [ "$ready" != 1 ]; then echo "[run] server not ready in 240s"; tail -30 server-log.txt; kill "$SRV" 2>/dev/null; exit 1; fi
echo "[run] server ready"

SCC_OUT="capture-$PHASE.json" "$NODE" ../testbot/bot-mf.js
BOT=$?
echo "[run] bot exit=$BOT"

kill "$SRV" 2>/dev/null
sleep 3
kill -9 "$SRV" 2>/dev/null
echo "[run] === server log: SCC + chat lines ==="
grep -aE "SuperColourfulChat|TestBot|gradient" server-log.txt | tail -25
exit $BOT