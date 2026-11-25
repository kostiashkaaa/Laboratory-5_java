#!/usr/bin/env bash
# Сборка без Maven/Gradle: компиляция (с предупреждениями как ошибками), самопроверка, упаковка в dist/laba5.jar.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out/build out/test-build
mkdir -p out/build out/test-build dist

echo "1/3 Компиляция…"
javac -encoding UTF-8 -Xlint:all,-serial -Werror --release 17 -d out/build $(find src -name "*.java")
javac -encoding UTF-8 -Xlint:all,-serial -Werror --release 17 -cp out/build -d out/test-build $(find test -name "*.java")

echo "2/3 Самопроверка…"
java -Djava.awt.headless=true -cp out/build:out/test-build ru.spbstu.mdp.SelfTest

echo "3/3 Упаковка…"
rm -f dist/laba5.jar
jar --create --file dist/laba5.jar --main-class ru.spbstu.mdp.Main -C out/build .
echo "Готово: dist/laba5.jar  (запуск: java -jar dist/laba5.jar)"
