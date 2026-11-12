#!/usr/bin/env bash
# Reproducible build without Maven/Gradle:
#   1. download compileOnly deps into libs/ (never shipped in the jar)
#   2. compile with JDK17 --release 8 against spigot-api 1.16.5 + EssentialsX 2.19.4 (delivery)
#   3. compile the same sources with JDK21 against the latest readable paper-api + EssentialsX 2.22.0 (upper bound proof)
#   4. package build/SuperColourfulChat.jar and verify major version 52
set -euo pipefail
cd "$(dirname "$0")"

JDK_LOWER="${JDK_LOWER:-D:/java17_zulu}"
JDK_UPPER="${JDK_UPPER:-D:/JAVA21}"
JAVAC="$JDK_LOWER/bin/javac.exe"
JAR="$JDK_LOWER/bin/jar.exe"
JAVAP="$JDK_LOWER/bin/javap.exe"
JAVAC_UPPER="$JDK_UPPER/bin/javac.exe"

CENTRAL="https://repo.maven.apache.org/maven2"
PAPERMC="https://repo.papermc.io/repository/maven-public"
SPIGOTMC="https://hub.spigotmc.org/nexus/content/repositories/snapshots"
ESSX="https://repo.essentialsx.net/releases"
ESSX_SNAP="https://repo.essentialsx.net/snapshots"

ADVENTURE_VER="4.26.1"
EXAMINATION_VER="1.3.0"
BUNGEE_UPPER_VER="1.21-R0.1"

log() { printf '[build] %s\n' "$*"; }

mkdir -p libs build

fetch() { # fetch <url> <dest>
    local url="$1" dest="$2"
    [ -s "$dest" ] && return 0
    log "download $url"
    if curl -fL --retry 3 --retry-delay 2 --connect-timeout 30 -o "$dest.part" "$url"; then
        mv -f "$dest.part" "$dest"
    else
        rm -f "$dest.part"
        return 1
    fi
}

snapshot_jar() { # snapshot_jar <repo> <groupPath> <artifact> <version> <dest> [fallbackRepo]
    local repo="$1" group="$2" artifact="$3" version="$4" dest="$5" fallback="${6:-}"
    [ -s "$dest" ] && return 0
    local base="${version%-SNAPSHOT}" meta ts bn
    meta="$(curl -fsSL --retry 3 --connect-timeout 30 "$repo/$group/$artifact/$version/maven-metadata.xml" || true)"
    ts="$(printf '%s\n' "$meta" | grep -o '<timestamp>[^<]*' | head -n1 | cut -d'>' -f2 || true)"
    bn="$(printf '%s\n' "$meta" | grep -o '<buildNumber>[^<]*' | head -n1 | cut -d'>' -f2 || true)"
    if [ -n "$ts" ] && [ -n "$bn" ]; then
        if fetch "$repo/$group/$artifact/$version/$artifact-$base-$ts-$bn.jar" "$dest"; then
            return 0
        fi
    fi
    if [ -n "$fallback" ]; then
        snapshot_jar "$fallback" "$group" "$artifact" "$version" "$dest"
        return $?
    fi
    fetch "$repo/$group/$artifact/$version/$artifact-$version.jar" "$dest"
}

log "== lower bound dependencies (spigot-api 1.16.5 / EssentialsX 2.19.4) =="
fetch "$CENTRAL/net/md-5/bungeecord-chat/1.16-R0.4/bungeecord-chat-1.16-R0.4.jar" libs/bungeecord-chat-1.16-R0.4.jar
snapshot_jar "$PAPERMC" org/spigotmc spigot-api 1.16.5-R0.1-SNAPSHOT libs/spigot-api-1.16.5-R0.1-SNAPSHOT.jar "$SPIGOTMC"

ESS_LOWER_JAR="libs/EssentialsX-2.19.4.jar"
if [ ! -s "$ESS_LOWER_JAR" ]; then
    if ! fetch "$ESSX/net/essentialsx/EssentialsX/2.19.4/EssentialsX-2.19.4.jar" "$ESS_LOWER_JAR"; then
        meta="$(curl -fsSL --retry 3 "$ESSX/net/essentialsx/EssentialsX/maven-metadata.xml")"
        ver="$(printf '%s\n' "$meta" | grep -o '<version>2\.19\.[^<]*' | cut -d'>' -f2 | tail -n1)"
        [ -n "$ver" ] || ver="$(printf '%s\n' "$meta" | grep -o '<version>2\.20\.[^<]*' | cut -d'>' -f2 | tail -n1)"
        [ -n "$ver" ] || { log "no EssentialsX 2.19.x/2.20.x available"; exit 1; }
        ESS_LOWER_JAR="libs/EssentialsX-$ver.jar"
        fetch "$ESSX/net/essentialsx/EssentialsX/$ver/EssentialsX-$ver.jar" "$ESS_LOWER_JAR"
    fi
fi

log "== upper bound dependencies (latest readable paper-api / EssentialsX 2.22.0) =="
fetch "$CENTRAL/net/kyori/adventure-api/$ADVENTURE_VER/adventure-api-$ADVENTURE_VER.jar" "libs/adventure-api-$ADVENTURE_VER.jar"
fetch "$CENTRAL/net/kyori/adventure-key/$ADVENTURE_VER/adventure-key-$ADVENTURE_VER.jar" "libs/adventure-key-$ADVENTURE_VER.jar"
fetch "$CENTRAL/net/kyori/examination-api/$EXAMINATION_VER/examination-api-$EXAMINATION_VER.jar" "libs/examination-api-$EXAMINATION_VER.jar"
fetch "$CENTRAL/net/md-5/bungeecord-chat/$BUNGEE_UPPER_VER/bungeecord-chat-$BUNGEE_UPPER_VER.jar" "libs/bungeecord-chat-$BUNGEE_UPPER_VER.jar"
UPPER_SIG_CP="libs/adventure-api-$ADVENTURE_VER.jar;libs/adventure-key-$ADVENTURE_VER.jar;libs/examination-api-$EXAMINATION_VER.jar;libs/bungeecord-chat-$BUNGEE_UPPER_VER.jar"

# javap tolerates newer class files, javac does not: probe-compile a reference to org.bukkit.Server.
paper_readable() { # paper_readable <jar>
    printf 'class SccProbe { org.bukkit.Server server; }\n' > build/SccProbe.java
    "$JAVAC_UPPER" -cp "$1;$UPPER_SIG_CP" -d build/probe build/SccProbe.java >/dev/null 2>&1
}

PAPER_JAR=""
for j in libs/paper-api-*.jar; do
    [ -s "$j" ] || continue
    if paper_readable "$j"; then PAPER_JAR="$j"; break; fi
done
if [ -z "$PAPER_JAR" ]; then
    meta="$(curl -fsSL --retry 3 "$PAPERMC/io/papermc/paper/paper-api/maven-metadata.xml")"
    ver="$(printf '%s\n' "$meta" | grep -o '<latest>[^<]*' | head -n1 | cut -d'>' -f2)"
    [ -n "$ver" ] || ver="$(printf '%s\n' "$meta" | grep -o '<version>[^<]*' | cut -d'>' -f2 | tail -n1)"
    [ -n "$ver" ] || { log "cannot resolve paper-api version"; exit 1; }
    jar="libs/paper-api-$ver.jar"
    case "$ver" in
        *-SNAPSHOT) snapshot_jar "$PAPERMC" io/papermc/paper paper-api "$ver" "$jar" ;;
        *) fetch "$PAPERMC/io/papermc/paper/paper-api/$ver/paper-api-$ver.jar" "$jar" ;;
    esac
    if paper_readable "$jar"; then
        PAPER_JAR="$jar"
    else
        log "paper-api $ver needs newer bytecode than $JDK_UPPER; falling back to latest 1.21.x"
        ver="$(printf '%s\n' "$meta" | grep -o '<version>1\.21[^<]*' | cut -d'>' -f2 | grep -vE 'rc|pre|no-moonrise' | tail -n1)"
        [ -n "$ver" ] || { log "no 1.21.x fallback found"; exit 1; }
        jar="libs/paper-api-$ver.jar"
        snapshot_jar "$PAPERMC" io/papermc/paper paper-api "$ver" "$jar"
        paper_readable "$jar" || { log "fallback paper-api $ver still unreadable"; exit 1; }
        PAPER_JAR="$jar"
    fi
fi
log "upper paper-api: $PAPER_JAR"

ESS_UPPER_JAR="libs/EssentialsX-2.22.0.jar"
if [ ! -s "$ESS_UPPER_JAR" ] && ! fetch "$ESSX/net/essentialsx/EssentialsX/2.22.0/EssentialsX-2.22.0.jar" "$ESS_UPPER_JAR"; then
    ESS_UPPER_JAR="libs/EssentialsX-2.22.0-SNAPSHOT.jar"
    if ! snapshot_jar "$ESSX_SNAP" net/essentialsx EssentialsX 2.22.0-SNAPSHOT "$ESS_UPPER_JAR"; then
        ver="$(curl -fsSL --retry 3 "$ESSX/net/essentialsx/EssentialsX/maven-metadata.xml" | grep -o '<release>[^<]*' | cut -d'>' -f2)"
        [ -n "$ver" ] || { log "cannot resolve EssentialsX upper version"; exit 1; }
        ESS_UPPER_JAR="libs/EssentialsX-$ver.jar"
        fetch "$ESSX/net/essentialsx/EssentialsX/$ver/EssentialsX-$ver.jar" "$ESS_UPPER_JAR"
    fi
fi
log "upper EssentialsX: $ESS_UPPER_JAR"

log "== compile =="
find src/main/java -name '*.java' | sort > build/sources.txt
rm -rf build/classes build/classes-upper
mkdir -p build/classes build/classes-upper

LOWER_CP="libs/spigot-api-1.16.5-R0.1-SNAPSHOT.jar;libs/bungeecord-chat-1.16-R0.4.jar;$ESS_LOWER_JAR"
"$JAVAC" --release 8 -encoding UTF-8 -cp "$LOWER_CP" -d build/classes @build/sources.txt
log "lower bound compile OK (--release 8, spigot-api 1.16.5, $ESS_LOWER_JAR)"

UPPER_CP="$PAPER_JAR;$ESS_UPPER_JAR;$UPPER_SIG_CP"
"$JAVAC_UPPER" -encoding UTF-8 -cp "$UPPER_CP" -d build/classes-upper @build/sources.txt
log "upper bound compile OK (JDK21 default, $PAPER_JAR, $ESS_UPPER_JAR)"

"$JAR" --create --file build/SuperColourfulChat.jar -C build/classes . -C src/main/resources .

log "== verify =="
"$JAVAP" -verbose -cp build/classes com.supercolourfulchat.SuperColourfulChatPlugin | grep -a 'major version'
"$JAVAP" -verbose -cp build/classes com.supercolourfulchat.SuperColourfulChatPlugin | grep -aq 'major version: 52' || { log "FAIL: main class not major 52"; exit 1; }
fail=0
while IFS= read -r f; do
    major="$(od -An -tu1 -j7 -N1 "$f" | tr -d ' ')"
    if [ "$major" != "52" ]; then log "FAIL: $f major=$major"; fail=1; fi
done < <(find build/classes -name '*.class')
[ "$fail" = 0 ] || exit 1
"$JAR" --list --file build/SuperColourfulChat.jar | grep -q '^plugin.yml$' || { log "FAIL: plugin.yml missing from jar root"; exit 1; }
log "all classes major 52, plugin.yml present"
log "DONE: $(pwd)/build/SuperColourfulChat.jar"