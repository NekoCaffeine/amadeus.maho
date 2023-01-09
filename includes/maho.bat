@echo off

if not "%MAHO_JDK%" == "" set "JAVA_HOME=%MAHO_JDK%"
if "%JAVA_HOME%" == "" set "JAVA_CMD=java"
if not "%JAVA_HOME%" == "" set "JAVA_CMD=%JAVA_HOME%\bin\java"

set "COMMAND="%JAVA_CMD%" %MAHO_OPTS% -p "%MAHO_HOME%\modules;" -m amadeus.maho %*"
%COMMAND%
