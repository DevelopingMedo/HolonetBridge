# Holonet Bridge

Holonet Bridge is a self-hosted Java application that connects Discord to one configured ticket provider. It is packaged as an executable Spring Boot JAR and is intended to run in the operator's own Docker container. The operator controls the host, provider URL, credentials, backups, and secret storage; this project does not host customer credentials.

## Current foundation

This is the bare Spring Boot skeleton: a single `HolonetBridgeApplication` entry point with no provider or Discord integration yet.

## Build and test

Requires Java 25 for the currently selected Spring Boot baseline.

```sh
mvn test
mvn package
```
