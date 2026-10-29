package de.medo.holonetbridge.commons.extapi;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;

public interface TicketProvider {

    void create(HolonetReportFormData data);
}
