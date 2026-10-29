package de.medo.holonetbridge.commons.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

@Entity
@Data
@Table(name = "HOLONET_SETUP_FORM_DATA")
public class HolonetSetupFormData {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "setup_form_data_generator")
    @SequenceGenerator(
            name = "setup_form_data_generator",
            sequenceName = "holonet_setup_form_data_seq",
            allocationSize = 1)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(name = "SESSION_ID", nullable = false, unique = true)
    private HolonetBotInteractionSession session;
    @Convert(converter = TicketProviderConverter.class)
    @Column(name = "TICKET_PROVIDER", length = 32)
    private TicketProvider ticketProvider;
    private String baseUrl;
    private String target;
    @Column(length = 2048)
    private String token;
    private Long supportChannelId;
    @Transient
    private boolean configLoaded;
}
