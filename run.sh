#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "==> Building frontend..."
cd client && npm install --silent && npm run build && cd ..

echo "==> Building backend..."
mvn package -q -DskipTests

echo "==> Launching..."
java -jar target/sf-pipeline-1.0.0.jar
