@echo off
set JAR=%~dp0target\RandomFetcher-1.3.1-SNAPSHOT-jar-with-dependencies.jar
java -jar "%JAR%" %*
