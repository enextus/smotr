@echo off
set JAR=%~dp0target\RandomFetcher-1.3.1-SNAPSHOT-jar-with-dependencies.jar
java --enable-preview ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     -jar "%JAR%" %*
