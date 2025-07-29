#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR=$(ls "$DIR"/target/RandomFetcher-*-jar-with-dependencies.jar 2>/dev/null | head -n1)

if [[ ! -f "$JAR" ]]; then
  echo "Fat‑JAR not found in target/. Did you run 'mvn package -DskipTests'?"
  exit 1
fi

java -jar "$JAR" "$@"
