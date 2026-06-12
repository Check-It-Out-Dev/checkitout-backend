@echo off
echo Building CheckItOut Backend with increased memory...
set MAVEN_OPTS=-Xmx2048m -XX:MaxPermSize=512m
echo MAVEN_OPTS set to: %MAVEN_OPTS%
call mvnw.cmd clean install -DskipTests
echo Build complete!
pause
