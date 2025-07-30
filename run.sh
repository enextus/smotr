#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR=$(ls "$DIR"/target/RandomFetcher-*-jar-with-dependencies.jar 2>/dev/null | head -n1)

if [[ -z "$JAR" ]]; then
  echo "Fat‑JAR not found. Run: mvn -DskipTests=true package"
  exit 1
fi

java -jar "$JAR" "$@"
