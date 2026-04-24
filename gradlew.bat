@if "%DEBUG%" == "" @echo off
@rem Gradle startup script for Windows

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

gradle %*
