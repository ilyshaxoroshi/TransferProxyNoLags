REM This is not the launch script for the build a .jar file for transfer proxy! 
REM Please create a dedicated folder, place the files there, and run "start-proxy.bat".


echo off
chcp 65001 >nul
title TransferProxyNoLags

REM ============================================================
REM  TransferProxyNoLags — Windows startup script
REM  Optimized JVM flags for running alongside Minecraft server
REM ============================================================

REM --- Heap: fixed 128MB, no resizing overhead ---
set "JVM_ARGS=-Xms128m -Xmx128m"

REM --- GC: SerialGC (no concurrent marking = no CPU spikes) ---
set "JVM_ARGS=%JVM_ARGS% -XX:+UseSerialGC"

REM --- Pre-commit heap pages (no page faults at runtime) ---
set "JVM_ARGS=%JVM_ARGS% -XX:+AlwaysPreTouch"

REM --- Ignore System.gc() calls ---
set "JVM_ARGS=%JVM_ARGS% -XX:+DisableExplicitGC"

REM === For multiple instances: rename jar or copy script per-instance ===
REM   Instance 1: TransferProxyNoLags-25565.jar  + port 25565 in config.yml
REM   Instance 2: TransferProxyNoLags-25566.jar  + port 25566
REM   Instance 3: TransferProxyNoLags-25567.jar  + port 25567

java %JVM_ARGS% -jar "%~dp0TransferProxyNoLags.jar"

pause
