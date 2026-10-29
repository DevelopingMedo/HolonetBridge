package de.medo.holonetbridge.commons.domain;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Data
@Table(name = "holonet_report_form_data", uniqueConstraints =
        @UniqueConstraint(name = "uk_holonet_report_session_kind", columnNames = {"session_id", "kind"}))
public class HolonetReportFormData {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "report_form_data_generator")
    @SequenceGenerator(name = "report_form_data_generator", sequenceName = "holonet_report_form_data_seq", allocationSize = 1)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private HolonetBotInteractionSession session;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HolonetTicketKind kind;
    @Column(length = 100)
    private String title;
    @Column(length = 4000)
    private String description;
    @Column(length = 4000)
    private String steps;
    @Column(length = 4000)
    private String expected;
    @Column(length = 4000)
    private String actual;
    @Column(length = 100)
    private String recurrence;
    @Column(length = 100)
    private String guildName;
    @Column(length = 100)
    private String username;
}
