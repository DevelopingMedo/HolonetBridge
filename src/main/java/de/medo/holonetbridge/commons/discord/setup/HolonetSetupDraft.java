package de.medo.holonetbridge.commons.discord.setup;

/**
 * State of one Discord user's in-progress {@code /setup} connection flow: which guild and panel channel it is
 * for, and the last-submitted non-secret field values (for prefilling a retry after a failed validation).
 * Never holds the token, so a retry can never leak a previously entered secret back into the modal.
 */
public final class HolonetSetupDraft {

    private final long guildId;
    private final long panelChannelId;

    private String baseUrl;
    private String owner;
    private String repository;

    public HolonetSetupDraft(long guildId, long panelChannelId) {
        this.guildId = guildId;
        this.panelChannelId = panelChannelId;
    }

    public long guildId() {
        return guildId;
    }

    public long panelChannelId() {
        return panelChannelId;
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

    /** Remembers the last-submitted non-secret target fields, so a retry can prefill them. */
    public void lastSubmittedTarget(String baseUrl, String owner, String repository) {
        this.baseUrl = baseUrl;
        this.owner = owner;
        this.repository = repository;
    }
}
