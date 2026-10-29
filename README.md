# Holonet Bridge

Holonet Bridge is a self-hosted Java application that connects Discord to one configured ticket provider. It is packaged as an executable Spring Boot JAR and is intended to run in the operator's own Docker container. The operator controls the host, provider URL, credentials, backups, and secret storage; this project does not host customer credentials.

## Current foundation

- A provider-neutral `TicketProvider` interface with one operation: `void create(HolonetReportFormData)`.
- A Gitea adapter that posts reports to the repository's issue API. No response DTO or unused CRUD methods are needed.
- Guild-specific connections configured through `/setup`; Bug and Idea modals save their draft and submit an external ticket asynchronously. See [the current setup and submission lifecycle](docs/ticket-submission.md). The older configuration and `/ticket` examples below are historical and do not describe this current flow.

The domain `TicketProvider` enum selects a provider during setup/restoration; the `commons.extapi.TicketProvider` interface performs ticket creation. Only Gitea is implemented.

Internally, created/edited tickets are still called "tickets" everywhere in this codebase and towards Discord users, even for the Gitea adapter, where they map onto Gitea's "issues". This keeps the user-facing and cross-provider vocabulary provider-agnostic.

## Package layout

The base package is `de.medo.holonetbridge`, with `HolonetBridgeApplication` kept directly in it. Spring Boot's default component scan covers every subpackage below, but configuration-properties binding is scanned only under `commons` (see "Provider-conditional wiring" below for why):

| Package | Contains |
| --- | --- |
| `commons` | Shared domain/persistence, encryption, Markdown, Discord interactions/localization, the ticket interface in `extapi`, and the guild-specific provider lifecycle in `provider`. |
| `gitea` | `GiteaProviderFactory` validates connection details and constructs `GiteaTicketProvider`; `GiteaHealthCheck` tests authentication. |
| `bootstrap` (tests) | Startup/smoke tests wiring the whole application context. |

### Provider lifecycle

`HolonetTicketProviderService` holds a ready-to-use provider per guild. It builds a candidate after a successful setup healthcheck, activates it only after the support panel was posted and the configuration transaction committed, and restores active configurations before the interaction listener can be used. Drafts are never active connections. Invalid stored connections are skipped individually so `/setup` remains available.

Each Gitea provider retains its own authenticated API client across tickets. Clients share one managed HTTP backend, with redirects and automatic retries disabled; shutdown closes its connection pool.

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

### Provider-token encryption at rest

`commons.crypto` provides `TokenCryptoService`, an AES-256-GCM service for encrypting a provider token before it is persisted: a fresh random nonce per encryption, and the Discord guild/provider-connection it belongs to bound in as authenticated associated data (`TokenEncryptionContext`), so a ciphertext read back under a different guild/connection fails to decrypt instead of silently succeeding. `EncryptedSecret` is the resulting ciphertext/nonce/key-version tuple meant for storage. There is no new provider-connection database entity in this work package yet — that lands later and persists `EncryptedSecret` instances — so `TokenCryptoService` is usable and tested standalone already.

The AES master key(s) come from `holonet.crypto.active-key-version` and `holonet.crypto.keys.<version>` (a Base64-encoded 256-bit key per version), supplied the same way as the provider/Discord tokens above — the operator's secret manager, never the database:

```
HOLONET_CRYPTO_ACTIVE_KEY_VERSION=v1
HOLONET_CRYPTO_KEYS_V1=<base64-encoded-256-bit-key>
```

Nothing in this work package requires these to be set (the application starts fine without them); they only matter once something actually calls `TokenCryptoService`. Missing or invalid key material fails fast with `MasterKeyUnavailableException` at the point a key is requested, never with a silently generated or otherwise substitute key. See [`docs/token-key-rotation.md`](docs/token-key-rotation.md) for the key-rotation procedure.

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

## Database schema migrations

Schema changes go through Liquibase (`src/main/resources/db/changelog/db.changelog-master.xml`) instead of Hibernate's own DDL generation. `spring.jpa.hibernate.ddl-auto` is `validate`, so Hibernate checks the entity mappings against the schema produced by Liquibase.

The development migration history has been reset to one baseline, `db.changelog-1.0-baseline.xml`, containing only the four tables used by the current entities: `holonet_support_channel`, `holonet_guild_sequence`, `holonet_bot_interaction_session`, and `holonet_setup_form_data`. Old development databases are not migrated to this baseline: stop the application and delete the configured H2 database file (`data/holonet-bridge.mv.db` by default) once, discarding its data. Liquibase creates the schema on the next start. This is a one-time development reset, not an automatic drop on every startup. For subsequent schema changes, add new changelog files; do not rename or rewrite applied changesets.

The default/`dev` H2 database is a file rather than in-memory, so data survives process/container restarts: `DB_FILE_PATH` (default `./data/holonet-bridge`) controls where. The `.ci/deployment` Compose setup mounts that path as a named volume (`holonet-bridge-data`) so it also survives container re-creation, not just process restarts.

`SchemaMigrationTest` (under `bootstrap`) starts the persistence context against a fresh database file and then reopens it, checking Hibernate validation, the exact set of tables, migration history, and persisted setup data without connecting to Discord.

## Build and test

Requires Java 25 for the currently selected Spring Boot baseline.

```sh
mvn test
mvn package
```

The provider port is the seam for isolated adapter tests: `GiteaTicketProviderTest` drives the Gitea adapter's HTTP calls (including a server-error response) through a `MockRestServiceServer`, without any real Gitea instance. Discord event handling is tested the same way on the other side of the boundary: `HolonetBridgeListenerAdapterTest` mocks the JDA event/action interfaces directly with Mockito (no gateway connection, no extra JDA-mock test library) to drive `HolonetBridgeListenerAdapter` through the whole button/modal flow.

`GiteaTicketProviderIntegrationTest` additionally drives `GiteaTicketProvider` against a real Gitea instance started with Testcontainers (image `gitea/gitea:1.22`, mirroring the init steps from `.ci/deployment/docker-compose.yml`). It is skipped automatically when no Docker environment is reachable (`disabledWithoutDocker`).

`SchemaMigrationTest` covers the Liquibase/Hibernate-validate migration setup from "Database schema migrations" above.

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
- Provider-connection persistence: `TokenCryptoService` (see "Provider-token encryption at rest" above) exists and is tested standalone, but nothing yet persists an `EncryptedSecret` for an actual per-guild provider connection — there is still only the single, globally configured `holonet.provider.*` connection. The entity that will own that and call `TokenCryptoService` is a later work package.

## Supported languages

- German
- English
- Croatian
- Russian (Latin script)

Personal replies and dialogs follow the user's Discord language. Public messages and
ticket descriptions use the server's language. Unsupported languages use English.
