package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetSupportChannelRepository extends JpaRepository<HolonetSupportChannel, Long> {

    /**
     * Legacy rows (created before {@code connectionId} existed) still waiting to be linked to the connection
     * they were actually created against; used only by the startup backfill.
     */
    List<HolonetSupportChannel> findByConnectionIdIsNullAndTicketIdIsNotNull();

    /**
     * The only supported way to look a channel up by its external ticket number: paired with the connection
     * it was created against, since two different connections (repositories/providers) can coincidentally
     * use the same ticket number. Never look up by {@code ticketId} alone.
     */
    Optional<HolonetSupportChannel> findByConnectionIdAndTicketId(UUID connectionId, String ticketId);
}
