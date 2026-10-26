# Legacy H2 fixture

`legacy-holonet-bridge.mv.db` is a pre-existing database file as it would have looked right before this
work package: created purely by Hibernate's old `ddl-auto: update` (no `DATABASECHANGELOG`/`DATABASECHANGELOGLOCK`
tables, since Liquibase never ran against it), containing the same two tables
(`holonet_discord_server`, `holonet_support_channel`, exact DDL from `db.changelog-1.0-baseline.xml`) and one
seeded row each:

| Table | Row |
| --- | --- |
| `holonet_discord_server` | `guild_id=1, channel_id=111, message_id=222` |
| `holonet_support_channel` | `channel_id=333, guild_id=1, number=1, requester_user_id=444, ticket_id='LEGACY-1'` |

`MigrationSmokeTest` copies this file into a throwaway temp directory before pointing a Spring context at
it, so the checked-in fixture itself is never modified by a test run. It is read-only historical data used
to prove that introducing Liquibase on top of an existing database neither fails nor loses data.

Regenerate it only if the baseline schema itself ever has to change (which should not happen; add a new
changelog file instead), using the same H2 version as this project's `com.h2database:h2` dependency:

```java
Class.forName("org.h2.Driver");
try (var c = DriverManager.getConnection("jdbc:h2:file:/path/to/legacy-holonet-bridge", "sa", "");
     var s = c.createStatement()) {
    s.execute("create table holonet_discord_server (channel_id bigint not null, guild_id bigint not null, "
            + "message_id bigint not null, primary key (guild_id))");
    s.execute("create table holonet_support_channel (channel_id bigint not null, guild_id bigint not null, "
            + "number bigint not null, requester_user_id bigint not null, ticket_id varchar(255), "
            + "primary key (channel_id))");
    s.execute("insert into holonet_discord_server (channel_id, guild_id, message_id) values (111, 1, 222)");
    s.execute("insert into holonet_support_channel (channel_id, guild_id, number, requester_user_id, ticket_id) "
            + "values (333, 1, 1, 444, 'LEGACY-1')");
}
```
