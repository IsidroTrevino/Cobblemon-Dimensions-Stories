#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COBBLEMON_SOURCE_DIR=${COBBLEMON_SOURCE_DIR:-"$PROJECT_DIR/../cobblemon"}
JAVA_21_HOME=${JAVA_21_HOME:-$(/usr/libexec/java_home -v 21)}
ADDON_JAR="$PROJECT_DIR/build/libs/cobblemon-dimensions-stories-0.1.0.jar"
SERVER_MODS_DIR="$COBBLEMON_SOURCE_DIR/fabric/runServer/mods"

if [ ! -f "$COBBLEMON_SOURCE_DIR/gradlew" ]; then
    echo "Cobblemon source workspace not found at: $COBBLEMON_SOURCE_DIR" >&2
    exit 1
fi

echo "Building the server-only Stories addon..."
env JAVA_HOME="$JAVA_21_HOME" "$PROJECT_DIR/gradlew" build
mkdir -p "$SERVER_MODS_DIR"
cp "$ADDON_JAR" "$SERVER_MODS_DIR/cobblemon-dimensions-stories-0.1.0.jar"

echo "Starting the dedicated Cobblemon server with Stories..."
echo "On first launch, review the Minecraft EULA in fabric/runServer/eula.txt."
cd "$COBBLEMON_SOURCE_DIR"
env JAVA_HOME="$JAVA_21_HOME" /bin/sh ./gradlew -PfabricOnly :fabric:runServer
