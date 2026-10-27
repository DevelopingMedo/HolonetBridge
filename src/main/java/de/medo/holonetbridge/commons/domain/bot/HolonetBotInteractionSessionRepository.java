package de.medo.holonetbridge.commons.domain.bot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetBotInteractionSessionRepository
        extends JpaRepository<HolonetBotInteractionSession, UUID> {

    List<HolonetBotInteractionSession> findAllByDiscordIdAndUserId(long discordId, long userId);

    /**
     * The most recently touched unfinished session for this {@code (discordId, userId)} pair, used by the
     * bug/feature report flow ({@code HolonetTicketSessionService}) to offer a previous, not-yet-completed
     * attempt back to the user as "resume" or "start over". More than one such session can exist for the
     * same pair (see the class Javadoc); only the newest is ever relevant for that check.
     */
    Optional<HolonetBotInteractionSession> findFirstByDiscordIdAndUserIdOrderByUpdatedAtDesc(
            long discordId, long userId);
}
