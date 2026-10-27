package de.medo.holonetbridge.commons.domain.setup;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The non-secret field values a Discord user has entered so far into the {@code /setup} Gitea connection
 * modal: which panel channel was chosen, and the last-submitted base URL/owner/repository target. One row
 * per {@link de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSession} (the row's own id), so it
 * survives an application restart the same way the session it belongs to does, letting {@code /setup} offer
 * "resume" instead of silently discarding an in-progress or failed attempt.
 *
 * <p>Deliberately never holds the submitted token: the token is only ever a short-lived local variable in
 * the modal-submission code path, so a resumed/prefilled modal can never leak a previously entered secret
 * back out, and this table never becomes a place an attacker with database access could read a live
 * credential from.
 */
@Entity
@Table(name = "holonet_setup_connection_modal")
public class HolonetSetupConnectionModal {

    @Id
    private UUID sessionId;

    private long channelId;
    private String baseUrl;
    private String owner;
    private String repository;

    protected HolonetSetupConnectionModal() {}

    public HolonetSetupConnectionModal(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public long channelId() {
        return channelId;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String owner() {
        return owner;
    }

    public String repository() {
        return repository;
    }

    /** Remembers the panel channel and non-secret target fields last submitted in the modal. */
    public void update(long channelId, String baseUrl, String owner, String repository) {
        this.channelId = channelId;
        this.baseUrl = baseUrl;
        this.owner = owner;
        this.repository = repository;
    }
}
