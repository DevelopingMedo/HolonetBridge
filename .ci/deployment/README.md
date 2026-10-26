# Dev deployment (Docker Compose)

Local Gitea plus the app's own built JAR, for integration testing.

## Start

```sh
mvn package
cd .ci/deployment
docker compose up -d
```

Every value has a working default, so no `.env` is required. Copy `env.example` to `.env` to override anything, including `SPRING_PROFILES_ACTIVE`.

On first start, `gitea-init` creates an admin user in Gitea, generates a real access token (picked up by the `app` container as `PROVIDER_TOKEN`), and creates the `GITEA_REPO_NAME` repository (default `support`) owned by that admin user, so issue creation works out of the box. The token is cached in the `gitea-token` volume, so it survives `docker compose down` (without `-v`).

The app's own H2 database file lives in the `holonet-bridge-data` volume (mounted at `/app/data`, the default `DB_FILE_PATH`), so it also survives `docker compose down`/container re-creation. Liquibase (see the main README's "Database schema migrations" section) applies its changesets to whatever is already in that volume on every start, rather than the app recreating or altering the schema itself.

The app always connects to the Discord gateway on startup; there is no way to switch that off. By default this setup uses a non-secret placeholder `DISCORD_TOKEN`, which fails to log in. Set a real `DISCORD_TOKEN` through your own secret manager/`.env` to exercise the live `/ticket` flow against a real Discord server.

Re-run `mvn package` and `docker compose up -d` after code changes.

## Stop

```sh
docker compose down
docker compose down -v
```

## Services

| Service | Port |
| --- | --- |
| `gitea` | 3000 (HTTP), 2222 (SSH) |
| `app` | 8080 |
