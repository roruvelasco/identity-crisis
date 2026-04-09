.PHONY: dev build server client

dev:
	@bash dev.sh

build:
	mvn clean compile

server:
	mvn exec:java@server

client:
	mvn javafx:run
