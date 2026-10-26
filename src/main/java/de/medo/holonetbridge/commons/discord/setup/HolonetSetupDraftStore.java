package de.medo.holonetbridge.commons.discord.setup;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Holds the in-progress {@link HolonetSetupDraft} for each Discord user currently going through the
 * {@code /setup} connection flow, keyed by Discord user id.
 *
 * <p>In-memory only, same short-lived-conversation pattern as {@code HolonetTicketCreationSessionStore}: a
 * draft is not meant to survive an application restart, and holds nothing more sensitive than the
 * non-secret target fields the user already typed into a now-rejected modal submission.
 */
@Component
public class HolonetSetupDraftStore {

    private final Map<Long, HolonetSetupDraft> drafts = new ConcurrentHashMap<>();

    public HolonetSetupDraft start(long userId, long guildId, long panelChannelId) {
        HolonetSetupDraft draft = new HolonetSetupDraft(guildId, panelChannelId);
        drafts.put(userId, draft);
        return draft;
    }

    public Optional<HolonetSetupDraft> get(long userId) {
        return Optional.ofNullable(drafts.get(userId));
    }

    public void clear(long userId) {
        drafts.remove(userId);
    }
}
