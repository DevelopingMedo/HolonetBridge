package de.medo.holonetbridge.commons.domain.bot;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One occurrence of a user working through a multi-step bot interaction flow (e.g. {@code /setup}, a bug or
 * feature report) that was not completed before the flow ended, so it can later be offered back to that user
 * as "resume" or "start over" instead of silently discarding their progress.
 *
 * <p>Deliberately not unique per {@code (discordId, userId)}: a user can have more than one such unfinished
 * occurrence outstanding (e.g. one per guild, or one left over from an earlier attempt), so lookups must
 * filter and choose among all matching rows rather than assume at most one exists.
 */
@Entity
@Table(name = "holonet_bot_interaction_session")
public class HolonetBotInteractionSession {

    @Id
    private UUID id;

    private long discordId;
    private long userId;

    private Instant createdAt;
    private Instant updatedAt;

    protected HolonetBotInteractionSession() {}

    public HolonetBotInteractionSession(UUID id, long discordId, long userId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.discordId = discordId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() {
        return id;
    }

    public long discordId() {
        return discordId;
    }

    public long userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
