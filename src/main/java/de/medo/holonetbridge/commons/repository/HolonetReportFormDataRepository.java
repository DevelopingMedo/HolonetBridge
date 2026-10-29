package de.medo.holonetbridge.commons.repository;

import java.util.Optional;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetReportFormDataRepository extends JpaRepository<HolonetReportFormData, Long> {
    Optional<HolonetReportFormData> findBySessionIdAndKind(Long sessionId, HolonetTicketKind kind);
}
