#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COBBLEMON_SOURCE_DIR=${COBBLEMON_SOURCE_DIR:-"$PROJECT_DIR/../cobblemon"}
JAVA_21_HOME=${JAVA_21_HOME:-$(/usr/libexec/java_home -v 21)}
CLIENT_MODS_DIR="$COBBLEMON_SOURCE_DIR/fabric/runClient/mods"

if [ ! -f "$COBBLEMON_SOURCE_DIR/gradlew" ]; then
    echo "Cobblemon source workspace not found at: $COBBLEMON_SOURCE_DIR" >&2
    exit 1
fi

for installed_jar in "$CLIENT_MODS_DIR"/cobblemon-dimensions-stories*.jar; do
    if [ -f "$installed_jar" ]; then
        echo "Remove Stories from the test client's mods folder before testing: $installed_jar" >&2
        exit 1
    fi
done

echo "Starting the Cobblemon client without Stories. Join the dedicated test server."
cd "$COBBLEMON_SOURCE_DIR"
env JAVA_HOME="$JAVA_21_HOME" /bin/sh ./gradlew -PfabricOnly --offline :fabric:runClient
