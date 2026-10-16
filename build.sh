#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
mkdir -p target/classes target/test-classes
javac -encoding UTF-8 --release 17 -d target/classes src/main/java/battleship/*.java
javac -encoding UTF-8 --release 17 -cp target/classes -d target/test-classes src/test/java/battleship/*.java
java -cp target/classes:target/test-classes battleship.SelfTest
jar --create --file battleship.jar -C target/classes .
