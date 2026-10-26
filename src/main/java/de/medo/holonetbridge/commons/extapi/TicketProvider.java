package de.medo.holonetbridge.commons.extapi;

import java.util.Optional;

public interface TicketProvider {

    TicketDto create(String title, String description);

    Optional<TicketDto> read(String id);

    TicketDto edit(String id, String title, String description);

    void comment(String id, String body);
}
