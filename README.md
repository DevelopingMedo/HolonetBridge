# Holonet Bridge

Holonet Bridge is a self-hosted Java application that connects Discord to one configured ticket provider. It is packaged as an executable Spring Boot JAR and is intended to run in the operator's own Docker container. The operator controls the host, provider URL, credentials, backups, and secret storage; this project does not host customer credentials.

## Current foundation

- A provider-neutral ticket port with create, read, edit, and comment operations. There is deliberately no delete operation.
- A Gitea adapter (`GiteaTicketProvider`) that implements `create` against a Gitea instance's issue API. `read`, `edit`, and `comment` are not implemented yet and throw `UnsupportedOperationException`.
- JDA (Java Discord API) as the Discord integration SDK, wired up and running an interactive `/ticket` creation flow end to end (see "The `/ticket` flow" below). Editing an existing ticket from Discord is not implemented yet.

The provider identifier is a string rather than a fixed enum so that future adapters are not constrained to a compile-time list. Only the Gitea adapter exists so far; other providers (Jira, ...) would plug into the same `TicketProvider` port.

Internally, created/edited tickets are still called "tickets" everywhere in this codebase and towards Discord users, even for the Gitea adapter, where they map onto Gitea's "issues". This keeps the user-facing and cross-provider vocabulary provider-agnostic.

## Package layout

The base package is `de.medo.holonetbridge`, with `HolonetBridgeApplication` kept directly in it. Spring Boot's default component scan covers every subpackage below, but configuration-properties binding is scanned only under `commons` (see "Provider-conditional wiring" below for why):

| Package | Contains |
| --- | --- |
| `commons` | Everything that is valid across providers: application-level configuration binding (`ProviderConnectionProperties`) directly in the package, `commons.ticket` for the boundary to external ticket systems (the `TicketProvider` port, taking plain `String` ids/fields, and its `TicketDto` return type), and `commons.discord` for the Discord-facing side, which is the one fixed consumer rather than a provider: `DiscordProperties` (bot token), `DiscordGatewayConfiguration` (builds and starts the JDA gateway, registers the `/ticket` command), `HolonetBridgeListenerAdapter` (the interactive multi-step flow), and the small `TicketCreationSession`/`TicketCreationSessionStore`/`TicketKind` types that hold a user's in-progress session between steps. |
| `gitea` | The Gitea provider adapter, as its own top-level package next to `commons`: `GiteaTicketProvider`, its request/response DTOs, `GiteaProperties` (the Gitea-specific owner/repository configuration, composed alongside `ProviderConnectionProperties` rather than extending it — see the Javadoc on `GiteaProperties` for why), and `GiteaConfiguration` (the conditional wiring that creates the two beans above only when Gitea is actually selected). Other providers (Jira, ...) would get their own top-level package the same way. |
| `bootstrap` (tests) | Startup/smoke tests wiring the whole application context. |

### Provider-conditional wiring

`GiteaProperties` and `GiteaTicketProvider` are not created unconditionally at every startup. `GiteaConfiguration` is a plain `@Configuration` class gated with `@ConditionalOnProperty(prefix = "holonet.provider", name = "id", havingValue = "gitea")`; it registers `GiteaProperties` via `@EnableConfigurationProperties` and builds `GiteaTicketProvider` explicitly with `new` in a `@Bean` method, rather than relying on `GiteaTicketProvider` being an auto-registered `@Component` or `GiteaProperties` being picked up by a blanket configuration-properties scan. `HolonetBridgeApplication`'s `@ConfigurationPropertiesScan` is narrowed to `commons` accordingly, since it is only meant to cover configuration that is relevant regardless of which provider is selected. Adding a second provider adapter means following the same pattern: its own `@Configuration` class, gated on its own `holonet.provider.id` value.

`TicketProvider` deliberately uses plain `String` parameters (id, title, description, comment body) instead of dedicated wrapper/DTO types for single or paired values; `TicketDto` remains a small record only because it is a genuine multi-field result (id, title, description) returned from create/read/edit.

## Configuration

Set these environment variables when starting the application:

| Variable | Meaning | Example (non-secret) |
| --- | --- | --- |
| `HOLONET_PROVIDER_ID` | Adapter key, such as `gitea` or `jira` | `gitea` |
| `HOLONET_PROVIDER_BASE_URL` | Base URL of the operator's provider instance | `https://tickets.example.invalid` |
| `HOLONET_PROVIDER_TOKEN` | Provider access token | supplied by the operator's secret manager |
| `HOLONET_PROVIDER_GITEA_OWNER` | Owner (user or organization) of the Gitea repository tickets are filed into | `acme` |
| `HOLONET_PROVIDER_GITEA_REPOSITORY` | Name of that Gitea repository | `support` |
| `HOLONET_DISCORD_TOKEN` | Discord bot/application token | supplied by the operator's secret manager |

The application fails at startup if a required value is missing or the provider URL is not an absolute HTTP(S) URL. The Discord gateway connects on every startup; there is currently no way to start the application with the gateway switched off (see "Known gaps" below). Spring binds these values once into an immutable singleton; there is no runtime configuration reload. Do not put real credentials in source control, image layers, command history, logs, or diagnostics. Use the deployment platform's secret mechanism (for example, Docker secrets or a protected environment injection). The provider token and Discord token are both intentionally redacted from their configuration objects' `toString()` output.

For example, a Compose service can pass values injected into its environment by the operator's deployment system:

```yaml
services:
  holonet-bridge:
    image: holonet-bridge:latest
    environment:
      HOLONET_PROVIDER_ID: gitea
      HOLONET_PROVIDER_BASE_URL: https://tickets.example.invalid
      HOLONET_PROVIDER_TOKEN: ${HOLONET_PROVIDER_TOKEN:?set this through your secret manager}
      HOLONET_PROVIDER_GITEA_OWNER: acme
      HOLONET_PROVIDER_GITEA_REPOSITORY: support
      HOLONET_DISCORD_TOKEN: ${HOLONET_DISCORD_TOKEN:?set this through your secret manager}
```

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

`src/main/resources/application-dev.yml` activates with the `dev` Spring profile and fills `holonet.provider.*` and `holonet.discord.*` from a few narrower, dev-only environment variables with defaults, so the application starts without having to set the `HOLONET_PROVIDER_*`/`HOLONET_DISCORD_*` variables by hand on every run:

| Variable | Default | Meaning |
| --- | --- | --- |
| `PROVIDER_BASE_URL` | `localhost` | Host (no scheme/port) of the local Gitea instance |
| `PROVIDER_PORT` | `3000` | Port of the local Gitea instance |
| `PROVIDER_TOKEN` | `a1b2c3d4` | Fixed, non-secret local placeholder token shared between the JAR and a locally started Gitea (for example, both started together through Docker Compose) |
| `PROVIDER_GITEA_OWNER` | `holonet` | Owner of the local Gitea repository tickets are filed into |
| `PROVIDER_GITEA_REPOSITORY` | `support` | Name of that local Gitea repository |
| `DISCORD_TOKEN` | `dev-placeholder-token` | Non-secret placeholder; replace with a real bot token to exercise the `/ticket` flow locally |

The resulting `holonet.provider.base-url` is `http://${PROVIDER_BASE_URL}:${PROVIDER_PORT}`; `holonet.provider.id` is fixed to `gitea`. The default provider token is a throwaway local value, not a real credential, and is fine to keep in version control for this purpose only — it must never be reused for a real Gitea instance that holds actual data.

The `.ci/deployment` Docker Compose setup (see its own [README](.ci/deployment/README.md)) wires these same variables end to end, including automatically creating the `PROVIDER_GITEA_REPOSITORY` repository in the local Gitea instance so ticket creation works without any manual setup.

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

The provider port is the seam for isolated adapter tests: `GiteaTicketProviderTest` drives the Gitea adapter's HTTP calls (including a server-error response) through a `MockRestServiceServer`, without any real Gitea instance. Discord event handling is tested the same way on the other side of the boundary: `HolonetBridgeListenerAdapterTest` mocks the JDA event/action interfaces directly with Mockito (no gateway connection, no extra JDA-mock test library) to drive `HolonetBridgeListenerAdapter` through the whole button/modal flow.

`GiteaTicketProviderIntegrationTest` additionally drives `GiteaTicketProvider` against a real Gitea instance started with Testcontainers (image `gitea/gitea:1.22`, mirroring the init steps from `.ci/deployment/docker-compose.yml`). It is skipped automatically when no Docker environment is reachable (`disabledWithoutDocker`).

Testcontainers auto-detects Docker via `/var/run/docker.sock`, but colima puts its socket at `~/.colima/default/docker.sock`. Expose it at the standard path once (requires sudo) and the test runs without any extra configuration:

```sh
sudo ln -sf "$HOME/.colima/default/docker.sock" /var/run/docker.sock
mvn test
```

The symlink also satisfies Testcontainers' Ryuk helper, which mounts the socket at the path the Docker daemon sees inside colima's VM (`/var/run/docker.sock`). If you cannot create the symlink, set these variables for the test run instead:

```sh
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
mvn test
```

## Known gaps

- Discord gateway enablement: there is currently no way to start the application with the gateway switched off. A previous `holonet.discord.enabled`/`HOLONET_DISCORD_ENABLED` toggle was removed because it only existed for test convenience; tests now mock the `JDA` bean directly instead (`@MockitoBean` in `BootstrapSmokeTest`). A proper toggle still needs a rework — likely tied to how a Discord bot is actually registered and obtains its token, which happens after the server is already running, not before.
