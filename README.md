# Holonet Bridge

Holonet Bridge is a self-hosted Java application that connects Discord to one configured ticket provider. It is packaged as an executable Spring Boot JAR and is intended to run in the operator's own Docker container. The operator controls the host, provider URL, credentials, backups, and secret storage; this project does not host customer credentials.

## Current foundation

- A provider-neutral ticket port with create, read, edit, and comment operations. There is deliberately no delete operation.
- JDA (Java Discord API) as the Discord integration SDK. Gateway startup and command handling have not been implemented yet.

The provider identifier is a string rather than a fixed enum so that future adapters are not constrained to a compile-time list. No provider implementation makes outbound requests yet.

## Package layout

The base package is `de.medo.holonetbridge`, with `HolonetBridgeApplication` kept directly in it so Spring Boot's default component/configuration-properties scan covers every subpackage below:

| Package | Contains |
| --- | --- |
| `domain` | Framework-free core model. Currently empty; reserved for future internal ticket/request domain types that are not just transfer shapes for the provider boundary. |
| `configuration` | Application-level wiring and configuration binding, currently `ProviderConnectionProperties`. |
| `extapi.ticket` | The boundary to external ticket systems: the `TicketProvider` port, taking plain `String` ids/fields, and its `TicketDto` return type. Concrete provider adapters (Gitea, Jira, ...) and, later, Discord-facing integration code also belong under `extapi`. |
| `bootstrap` (tests) | Startup/smoke tests wiring the whole application context. |

`TicketProvider` deliberately uses plain `String` parameters (id, title, description, comment body) instead of dedicated wrapper/DTO types for single or paired values; `TicketDto` remains a small record only because it is a genuine multi-field result (id, title, description) returned from create/read/edit.

## Configuration

Set these environment variables when starting the application:

| Variable | Meaning | Example (non-secret) |
| --- | --- | --- |
| `HOLONET_PROVIDER_ID` | Adapter key, such as `gitea` or `jira` | `gitea` |
| `HOLONET_PROVIDER_BASE_URL` | Base URL of the operator's provider instance | `https://tickets.example.invalid` |
| `HOLONET_PROVIDER_TOKEN` | Provider access token | supplied by the operator's secret manager |

The application fails at startup if a required value is missing or the URL is not an absolute HTTP(S) URL. Spring binds these values once into an immutable singleton; there is no runtime configuration reload. Do not put real credentials in source control, image layers, command history, logs, or diagnostics. Use the deployment platform's secret mechanism (for example, Docker secrets or a protected environment injection). The token is intentionally redacted from the configuration object's `toString()` output.

For example, a Compose service can pass values injected into its environment by the operator's deployment system:

```yaml
services:
  holonet-bridge:
    image: holonet-bridge:latest
    environment:
      HOLONET_PROVIDER_ID: gitea
      HOLONET_PROVIDER_BASE_URL: https://tickets.example.invalid
      HOLONET_PROVIDER_TOKEN: ${HOLONET_PROVIDER_TOKEN:?set this through your secret manager}
```

This initial configuration covers the destination provider only. The Discord bot/application token and Discord interaction setup still need a separate configuration and lifecycle design before JDA is started.

### Local development against a local Gitea

`src/main/resources/application-dev.yml` activates with the `dev` Spring profile and fills `holonet.provider.*` from a few narrower, dev-only environment variables with defaults, so the application starts without having to set the three `HOLONET_PROVIDER_*` variables by hand on every run:

| Variable | Default | Meaning |
| --- | --- | --- |
| `PROVIDER_BASE_URL` | `localhost` | Host (no scheme/port) of the local Gitea instance |
| `PROVIDER_PORT` | `3000` | Port of the local Gitea instance |
| `PROVIDER_TOKEN` | `a1b2c3d4` | Fixed, non-secret local placeholder token shared between the JAR and a locally started Gitea (for example, both started together through Docker Compose) |

The resulting `holonet.provider.base-url` is `http://${PROVIDER_BASE_URL}:${PROVIDER_PORT}`; `holonet.provider.id` is fixed to `gitea`. The default token is a throwaway local value, not a real credential, and is fine to keep in version control for this purpose only — it must never be reused for a real Gitea instance that holds actual data.

Activate the profile with `SPRING_PROFILES_ACTIVE=dev`, for example:

```sh
SPRING_PROFILES_ACTIVE=dev java -jar target/holonet-bridge-*.jar
```

## Build and test

Requires Java 25 for the currently selected Spring Boot baseline.

```sh
mvn test
mvn package
```

The provider port is the seam for isolated adapter tests. When Discord event handling is added, test it through a narrow application boundary and mocked/fake Discord-facing collaborators; the current foundation makes no Discord or provider network calls.
