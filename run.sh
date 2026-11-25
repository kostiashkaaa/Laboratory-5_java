#!/usr/bin/env bash
# Сборка и запуск без Maven/Gradle.
#   ./run.sh                 — GUI
#   ./run.sh console [опции] — текстовый режим (напр. --reward -0.3 --gamma 0.9 --risk-demo)
#   ./run.sh test            — самопроверка
#   ./run.sh snapshot [файл] — сохранить картинку визуализации (по умолчанию visualization.png)
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out/build
mkdir -p out/build
javac -encoding UTF-8 --release 17 -d out/build $(find src -name "*.java")

mode="${1:-gui}"
[[ $# -gt 0 ]] && shift
case "$mode" in
  gui)      java -cp out/build ru.spbstu.mdp.Main ;;
  console)  java -cp out/build ru.spbstu.mdp.Main --console "$@" ;;
  snapshot) java -Djava.awt.headless=true -cp out/build ru.spbstu.mdp.Main --snapshot "${1:-visualization.png}" ;;
  test)
    rm -rf out/test-build && mkdir -p out/test-build
    javac -encoding UTF-8 --release 17 -cp out/build -d out/test-build $(find test -name "*.java")
    java -Djava.awt.headless=true -cp out/build:out/test-build ru.spbstu.mdp.SelfTest ;;
  *) echo "Неизвестный режим: $mode (gui | console | test | snapshot)"; exit 1 ;;
esac
