package de.medo.holonetbridge.commons.extapi;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;

import java.util.Locale;

public interface TicketProvider {

    TicketReference create(HolonetReportFormData data);
    Locale fetchAccountLocale();

    boolean supports(TicketReference reference);

    void close(TicketReference reference);
}
