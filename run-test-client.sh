#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COBBLEMON_SOURCE_DIR=${COBBLEMON_SOURCE_DIR:-"$PROJECT_DIR/../cobblemon-main"}
JAVA_21_HOME=${JAVA_21_HOME:-$(/usr/libexec/java_home -v 21)}
ADDON_JAR="$PROJECT_DIR/build/libs/chilin-story-0.1.0.jar"
CLIENT_MODS_DIR="$COBBLEMON_SOURCE_DIR/fabric/runClient/mods"

if [ ! -f "$COBBLEMON_SOURCE_DIR/gradlew" ]; then
    echo "Cobblemon source workspace not found at: $COBBLEMON_SOURCE_DIR" >&2
    exit 1
fi

echo "Building Chilin Story..."
env JAVA_HOME="$JAVA_21_HOME" "$PROJECT_DIR/gradlew" --offline build

echo "Installing Chilin Story into the Fabric test client..."
mkdir -p "$CLIENT_MODS_DIR"
cp "$ADDON_JAR" "$CLIENT_MODS_DIR/chilin-story-0.1.0.jar"

echo "Starting Minecraft 1.21.1 with Cobblemon and Chilin Story..."
cd "$COBBLEMON_SOURCE_DIR"
env JAVA_HOME="$JAVA_21_HOME" /bin/sh ./gradlew -PfabricOnly --offline :fabric:runClient
