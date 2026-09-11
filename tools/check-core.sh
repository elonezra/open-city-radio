#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
radio_check_dir=$(mktemp -d)
trap 'rm -rf "$radio_check_dir"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$radio_check_dir" app/src/main/java/com/opencity/radio/LiveClock.java app/src/main/java/com/opencity/radio/SafeZip.java tools/CoreChecks.java
java -cp "$radio_check_dir" CoreChecks
