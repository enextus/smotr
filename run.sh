#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/target/RandomFetcher-1.3.1-SNAPSHOT-jar-with-dependencies.jar" "$@"
