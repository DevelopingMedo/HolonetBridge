package de.medo.holonetbridge.commons.domain.ticket;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetTicketRepository extends JpaRepository<HolonetTicket, UUID> {

    /** The draft ticket belonging to a session, if any; at most one ever exists (see {@code HolonetTicketSessionService}). */
    Optional<HolonetTicket> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
