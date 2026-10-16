@echo off
cd /d "%~dp0"
if not exist config\db.properties (
  copy config\db.properties.example config\db.properties >nul
  echo Created config\db.properties.
  echo Open it in Notepad and set the URL, username, and password for your SQL Server.
) else (
  echo config\db.properties already exists.
)
notepad config\db.properties
pause
