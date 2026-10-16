@echo off
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo JDK 17 or newer with javac is required.
  pause
  exit /b 1
)
if not exist target\classes mkdir target\classes
if not exist target\test-classes mkdir target\test-classes
javac -encoding UTF-8 --release 17 -d target\classes src\main\java\battleship\*.java
if errorlevel 1 goto failed
javac -encoding UTF-8 --release 17 -cp target\classes -d target\test-classes src\test\java\battleship\*.java
if errorlevel 1 goto failed
java -cp "target\classes;target\test-classes" battleship.SelfTest
if errorlevel 1 goto failed
jar --create --file battleship.jar -C target\classes .
if errorlevel 1 goto failed
echo Build complete. In NetBeans, open the project and wait for Maven to resolve Microsoft JDBC.
pause
exit /b 0
:failed
echo Build or self-test failed. Read the error above.
pause
exit /b 1
