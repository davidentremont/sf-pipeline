#!/bin/bash
set -e

cd client && npm install && npm run build && cd ..

mvn package -DskipTests

rm -rf dist
mkdir -p dist/data

cp target/sf-pipeline-1.0.0.jar dist/
cp launch.bat dist/

zip -r sf-pipeline-dist.zip dist/
rm -rf dist

echo "Done: sf-pipeline-dist.zip"
