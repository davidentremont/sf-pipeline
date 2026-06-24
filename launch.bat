@echo off
echo Starting SF Pipeline...
start "SF Pipeline" java -jar sf-pipeline-1.0.0.jar
timeout /t 4 /nobreak > nul
start http://localhost:8080
