#!/usr/bin/env bash
# usage: bash run-proxy2.sh <phase-name> <bot-script.js>
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TS="$ROOT/testserver"; TP="$ROOT/testproxy"
JAVA="D:/JAVA21/bin/java.exe"; NODE="D:/Node.js_24.21.0/node.exe"
PHASE="${1:-proxy}"; BOTSCRIPT="${2:-bot-mf.js}"

cd "$TS"; rm -f server-log.txt
"$JAVA" -Xmx1024M -Xms512M -jar leaves-1.21.8.jar --nogui > server-log.txt 2>&1 &
BACK=$!
echo "[proxy2] backend pid=$BACK phase=$PHASE bot=$BOTSCRIPT"
for i in $(seq 1 120); do
  grep -q 'Done (' server-log.txt 2>/dev/null && break
  kill -0 "$BACK" 2>/dev/null || { echo "[proxy2] backend died"; tail -20 server-log.txt; exit 1; }
  sleep 2
done
grep -q 'Done (' server-log.txt || { echo "[proxy2] backend not ready"; tail -20 server-log.txt; kill "$BACK"; exit 1; }
echo "[proxy2] backend ready"

cd "$TP"; rm -f proxy-log.txt
"$JAVA" -Xmx512M -jar BungeeCord.jar > proxy-log.txt 2>&1 &
PROXY=$!
echo "[proxy2] bungee pid=$PROXY"
for i in $(seq 1 60); do
  grep -q 'Listening on' proxy-log.txt 2>/dev/null && break
  kill -0 "$PROXY" 2>/dev/null || { echo "[proxy2] bungee died"; tail -25 proxy-log.txt; kill "$BACK"; exit 1; }
  sleep 1
done
grep -q 'Listening on' proxy-log.txt || { echo "[proxy2] bungee not ready"; tail -25 proxy-log.txt; kill "$BACK" "$PROXY"; exit 1; }
echo "[proxy2] bungee ready"

cd "$TS"
SCC_OUT="capture-$PHASE.json" SCC_VERSION="1.20.4" SCC_PORT="25565" "$NODE" "../testbot/$BOTSCRIPT"
BOT=$?
kill "$PROXY" "$BACK" 2>/dev/null; sleep 3; kill -9 "$PROXY" "$BACK" 2>/dev/null
echo "[proxy2] === bungee log (Via/load) ==="
grep -aiE 'Via|Listening|Enabled plugin|Error loading|Exception|SEVERE' "$TP/proxy-log.txt" | tail -25
echo "[proxy2] === backend SCC log ==="
grep -aE 'SuperColourfulChat|ColorfulChat|TestBot|gradient' "$TS/server-log.txt" | tail -20
exit $BOT