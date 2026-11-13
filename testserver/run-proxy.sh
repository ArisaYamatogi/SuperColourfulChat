#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TS="$ROOT/testserver"
TP="$ROOT/testproxy"
JAVA="D:/JAVA21/bin/java.exe"
NODE="D:/Node.js_24.21.0/node.exe"
PHASE="${1:-proxy}"

cd "$TS"; rm -f server-log.txt
"$JAVA" -Xmx1024M -Xms512M -jar leaves-1.21.8.jar --nogui > server-log.txt 2>&1 &
BACK=$!
echo "[proxy] backend pid=$BACK"
for i in $(seq 1 120); do
  grep -q 'Done (' server-log.txt 2>/dev/null && break
  kill -0 "$BACK" 2>/dev/null || { echo "[proxy] backend died"; tail -20 server-log.txt; exit 1; }
  sleep 2
done
grep -q 'Done (' server-log.txt || { echo "[proxy] backend not ready"; tail -20 server-log.txt; kill "$BACK"; exit 1; }
echo "[proxy] backend ready"

cd "$TP"; rm -f proxy-log.txt
"$JAVA" -Xmx512M -jar BungeeCord.jar > proxy-log.txt 2>&1 &
PROXY=$!
echo "[proxy] bungee pid=$PROXY"
for i in $(seq 1 60); do
  grep -q 'Listening on' proxy-log.txt 2>/dev/null && break
  kill -0 "$PROXY" 2>/dev/null || { echo "[proxy] bungee died"; tail -25 proxy-log.txt; kill "$BACK"; exit 1; }
  sleep 1
done
grep -q 'Listening on' proxy-log.txt || { echo "[proxy] bungee not ready"; tail -25 proxy-log.txt; kill "$BACK" "$PROXY"; exit 1; }
echo "[proxy] bungee ready"

cd "$TS"
SCC_OUT="capture-$PHASE.json" SCC_VERSION="1.20.4" SCC_PORT="25565" "$NODE" ../testbot/bot-mf.js
BOT=$?
kill "$PROXY" "$BACK" 2>/dev/null; sleep 3; kill -9 "$PROXY" "$BACK" 2>/dev/null
echo "[proxy] === bungee log ==="
grep -aE 'Via|Listening|TestBot|Exception|ERROR|WARN' "$TP/proxy-log.txt" | tail -20
echo "[proxy] === backend SCC log ==="
grep -aE 'SuperColourfulChat|TestBot|gradient' "$TS/server-log.txt" | tail -20
exit $BOT