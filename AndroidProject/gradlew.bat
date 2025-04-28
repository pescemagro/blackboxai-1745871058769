@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set APP_BASE_NAME=gradlew

set DEFAULT_JVM_OPTS=

set JAVA_EXE=

if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
goto execute

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if exist "%JAVA_EXE%" goto execute

echo Could not find java.exe in JAVA_HOME at %JAVA_EXE%
exit /b 1

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%DIRNAME%\..\lib\gradle-launcher-4.4.jar" org.gradle.launcher.GradleMain %*
