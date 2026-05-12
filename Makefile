.PHONY: dev build server client

dev:
	@if [ "$$(uname)" = "Darwin" ] || [ "$$(uname)" = "Linux" ]; then bash dev.sh; else powershell -ExecutionPolicy Bypass -File dev.ps1; fi

build:
	./mvnw clean compile

server:
	./mvnw exec:java@server

client:
	./mvnw javafx:run
