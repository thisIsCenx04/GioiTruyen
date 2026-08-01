@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0upload_vps.ps1" %*
