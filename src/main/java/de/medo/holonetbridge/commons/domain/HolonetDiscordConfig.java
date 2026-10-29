package de.medo.holonetbridge.commons.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class HolonetDiscordConfig {
    @Id
    private Long guildId;
    @Convert(converter = TicketProviderConverter.class)
    @Column(length = 32)
    private TicketProvider ticketProvider;
    private String baseUrl;
    private String target;
    @Column(length = 2048)
    private String token;
    private Long supportChannelId;
}
