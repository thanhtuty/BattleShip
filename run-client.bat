@echo off
cd /d "%~dp0"
java -cp "target\classes;battleship.jar" battleship.Client
pause
