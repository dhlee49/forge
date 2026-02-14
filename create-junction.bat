@echo off
mklink /J C:\f "C:\Users\Dong Hoon\Desktop\github\forge" > C:\tmp\junction.log 2>&1
dir C:\f\pom.xml >> C:\tmp\junction.log 2>&1
