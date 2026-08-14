@echo off
cd /d "E:\Dev\RSPS Project\2009scape\rt4-client"
call gradlew.bat compileJava 2>&1
echo EXIT_CODE=%ERRORLEVEL%
