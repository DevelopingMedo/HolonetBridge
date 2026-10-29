package de.medo.holonetbridge.commons.extapi;

import java.util.Optional;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;

public interface TicketProvider {

    TicketDto create(String title, String description);

    TicketDto create(HolonetReportFormData data);

    Optional<TicketDto> read(String id);

    TicketDto edit(String id, String title, String description);

    void comment(String id, String body);
}
