@echo off
cd /d "%~dp0"
set "PORT=5000"
if not "%~1"=="" set "PORT=%~1"
if not exist "config\db.properties" (
  echo Missing config\db.properties.
  echo Run setup-config.bat, then create the database using database\01_BattleShipDB.sql and database\02_CreateLogin.sql.
  pause
  exit /b 1
)
set "JDBC=%USERPROFILE%\.m2\repository\com\microsoft\sqlserver\mssql-jdbc\13.6.0.jre11\mssql-jdbc-13.6.0.jre11.jar"
if not exist "%JDBC%" (
  echo Cannot find Microsoft SQL Server JDBC Driver:
  echo %JDBC%
  echo Open the project in NetBeans and wait for Maven to resolve dependencies.
  echo Then run this file again.
  pause
  exit /b 1
)
java -cp "target\classes;battleship.jar;%JDBC%" battleship.Server %PORT%
pause
