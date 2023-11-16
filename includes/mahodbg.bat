@echo off

if not "%MAHO_JDK%" == "" set "JAVA_HOME=%MAHO_JDK%"
if "%JAVA_HOME%" == "" set "JAVA_CMD=java"
if not "%JAVA_HOME%" == "" set "JAVA_CMD=%JAVA_HOME%\bin\java"

set "MAHO_DBG_OPTS=%MAHO_OPTS% -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:36768"

set "COMMAND="%JAVA_CMD%" -XX:+EnableDynamicAgentLoading %MAHO_DBG_OPTS% -p "%MAHO_HOME%\modules;" -m amadeus.maho %*"
%COMMAND%
