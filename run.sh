#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java --enable-preview \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     -jar "$DIR/target/RandomFetcher-1.3.1-SNAPSHOT-jar-with-dependencies.jar" "$@"
