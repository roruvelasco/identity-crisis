#!/usr/bin/env bash
set -e

echo "[dev] Building..."
./mvnw clean compile -q

echo "[dev] Starting server..."
./mvnw exec:java@server &
SERVER_PID=$!

trap "echo '[dev] Stopping server...'; kill $SERVER_PID 2>/dev/null; wait $SERVER_PID 2>/dev/null" EXIT INT TERM

echo "[dev] Starting client..."
./mvnw javafx:run

wait $SERVER_PID 2>/dev/null || true
