.PHONY: dev build server client

dev:
	@bash dev.sh

build:
	./mvnw clean compile

server:
	./mvnw exec:java@server

client:
	./mvnw javafx:run
