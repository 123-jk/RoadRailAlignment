#!/bin/bash
set -e

# 编译
mkdir -p target/classes
javac -d target/classes -cp "lib/josm-tested.jar" $(find src/main/java -name "*.java")

# 创建MANIFEST
mkdir -p dist
cat > manifest.txt << 'MANIFEST'
Manifest-Version: 1.0
Plugin-Class: org.openstreetmap.josm.plugins.roadrailalignment.RoadRailPlugin
Plugin-Description: Draw road, rail, and ramp horizontal alignments.
zh_CN_Plugin-Description: 绘制道路、铁路和匝道平面线形。
fr_Plugin-Description: Dessine les alignements horizontaux de routes, rails et bretelles.
de_Plugin-Description: Zeichnet horizontale Trassierungen für Straßen, Schienen und Rampen.
es_Plugin-Description: Dibuja alineaciones horizontales de carreteras, ferrocarriles y ramales.
Plugin-Date: 2026-11-26
Plugin-Icon: images/mapmode/roadrailalignment.svg
Plugin-Mainversion: 14134
Plugin-Version: 0.2.4
Plugin-Canloadatruntime: true
MANIFEST

# 打包
"C:/Program Files/Java/jdk-21.0.12.1/bin/jar.exe" cfm dist/RoadRailAlignment.jar manifest.txt \
  -C target/classes . \
  -C src/main/resources .

echo "✓ JAR打包完成"
ls -lh dist/RoadRailAlignment.jar
