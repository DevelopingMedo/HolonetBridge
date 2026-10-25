# Holonet Bridge

Holonet Bridge is a self-hosted Java application that connects Discord to one configured ticket provider. It is packaged as an executable Spring Boot JAR and is intended to run in the operator's own Docker container. The operator controls the host, provider URL, credentials, backups, and secret storage; this project does not host customer credentials.

## Current foundation

- A provider-neutral ticket port with create, read, edit, and comment operations. There is deliberately no delete operation. No concrete provider adapter exists yet.
- JDA (Java Discord API) as the Discord integration SDK, wired up and running the interactive `/ticket` creation flow (see "The `/ticket` flow" below) through the `TicketProvider` port. Since no concrete provider adapter exists yet, the final create step has nothing to call. Editing an existing ticket from Discord is not implemented yet.

The provider identifier is a string rather than a fixed enum so that future adapters are not constrained to a compile-time list.

## Package layout

The base package is `de.medo.holonetbridge`, with `HolonetBridgeApplication` kept directly in it.

| Package | Contains |
| --- | --- |
| `commons` | Everything that is valid across providers: application-level configuration binding (`ProviderConnectionProperties`) directly in the package, `commons.ticket` for the boundary to external ticket systems (the `TicketProvider` port, taking plain `String` ids/fields, and its `TicketDto` return type), and `commons.discord` for the Discord-facing side, which is the one fixed consumer rather than a provider: `DiscordProperties` (bot token), `DiscordGatewayConfiguration` (builds and starts the JDA gateway, registers the `/ticket` command), `HolonetBridgeListenerAdapter` (the interactive multi-step flow), and the small `TicketCreationSession`/`TicketCreationSessionStore`/`TicketKind` types that hold a user's in-progress session between steps. |

`TicketProvider` deliberately uses plain `String` parameters (id, title, description, comment body) instead of dedicated wrapper/DTO types for single or paired values; `TicketDto` remains a small record only because it is a genuine multi-field result (id, title, description) returned from create/read/edit.

## Configuration

Set these environment variables when starting the application:

| Variable | Meaning | Example (non-secret) |
| --- | --- | --- |
| `HOLONET_PROVIDER_ID` | Adapter key, such as `gitea` or `jira` | `gitea` |
| `HOLONET_PROVIDER_BASE_URL` | Base URL of the operator's provider instance | `https://tickets.example.invalid` |
| `HOLONET_PROVIDER_TOKEN` | Provider access token | supplied by the operator's secret manager |
| `HOLONET_DISCORD_TOKEN` | Discord bot/application token | supplied by the operator's secret manager |

The application fails at startup if a required value is missing or the provider URL is not an absolute HTTP(S) URL. The Discord gateway connects on every startup; there is currently no way to start the application with the gateway switched off. Spring binds these values once into an immutable singleton; there is no runtime configuration reload. Do not put real credentials in source control, image layers, command history, logs, or diagnostics. Use the deployment platform's secret mechanism (for example, Docker secrets or a protected environment injection). The provider token and Discord token are both intentionally redacted from their configuration objects' `toString()` output.

Note: no concrete provider adapter exists yet, so `HOLONET_PROVIDER_ID` has no effect so far; the ticket-creation flow cannot actually file a ticket anywhere until one is added.

### The `/ticket` flow

Once the gateway is connected, the bot registers a single global `/ticket` slash command (with no options) and drives the rest of the conversation through buttons and modals instead of command arguments:

1. `/ticket` replies (ephemerally) with three buttons: **Anlegen** (create), **Ändern** (change), **Abbruch** (cancel).
2. **Anlegen** opens a modal asking for the ticket title.
3. After the title is submitted, the bot asks whether it is a **Bug** or a **Feature** (two buttons).
4. Depending on the answer, a description modal opens:
   - **Bug**: pre-filled with a reproduction-steps template (steps to reproduce, expected vs. actual behavior) for the user to fill in.
   - **Feature**: an empty field with placeholder guidance to describe, as concretely as possible, what should be possible afterwards.
5. On submission, the bot creates the ticket through the configured `TicketProvider` (title prefixed with `[Bug]`/`[Feature]`) and ephemerally confirms the resulting ticket id and title.

**Ändern** currently replies that editing is not implemented yet; **Abbruch** clears the in-progress session and confirms the cancellation. All bot replies in this flow are ephemeral (visible only to the user who ran `/ticket`). The bot connects with zero Discord gateway intents (`JDABuilder.createLight`) since the whole flow only needs slash-command/component/modal interactions, not any cached guild/member/message state.

### Local development against a local Gitea

`src/main/resources/application-dev.yml` activates with the `dev` Spring profile and defaults `holonet.provider.id` to `gitea` and the rest of `holonet.provider.*`/`holonet.discord.*` from a few narrower, dev-only environment variables, so the application starts without having to set the `HOLONET_PROVIDER_*`/`HOLONET_DISCORD_*` variables by hand on every run:

| Variable | Default | Meaning |
| --- | --- | --- |
| `PROVIDER_BASE_URL` | `localhost` | Host (no scheme/port) of the local Gitea instance |
| `PROVIDER_PORT` | `3000` | Port of the local Gitea instance |
| `PROVIDER_TOKEN` | `a1b2c3d4` | Fixed, non-secret local placeholder token shared between the JAR and a locally started Gitea (for example, both started together through Docker Compose) |
| `DISCORD_TOKEN` | `dev-placeholder-token` | Non-secret placeholder; replace with a real bot token to exercise the `/ticket` flow locally |

The resulting `holonet.provider.base-url` is `http://${PROVIDER_BASE_URL}:${PROVIDER_PORT}`. The default provider token is a throwaway local value, not a real credential, and is fine to keep in version control for this purpose only — it must never be reused for a real Gitea instance that holds actual data. No Gitea adapter exists yet (see "Known gaps"), so `holonet.provider.*` has no effect so far; these defaults are prepared ahead of it.

The `.ci/deployment` Docker Compose setup (see its own [README](.ci/deployment/README.md)) brings up a local Gitea server plus the application's own built JAR, wired to these same variables, as a ready-to-use local environment for the Gitea adapter once it exists.

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

Discord event handling is tested without any real gateway connection: `HolonetBridgeListenerAdapterTest` mocks the JDA event/action interfaces directly with Mockito (no extra JDA-mock test library) to drive `HolonetBridgeListenerAdapter` through the whole button/modal flow.

## Known gaps

- No concrete `TicketProvider` adapter exists yet; the `/ticket` flow cannot actually file a ticket anywhere until one is added (a Gitea adapter is next).
- Discord gateway enablement: there is currently no way to start the application with the gateway switched off. The dev profile (`application-dev.yml`) and `.ci/deployment` (`docker-compose.yml`, `env.example`) set a `DISCORD_ENABLED` variable that currently has no effect; this needs a proper rework — likely tied to how a Discord bot is actually registered and obtains its token, which happens after the server is already running, not before.
