package de.medo.holonetbridge.commons.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

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
    private Long nextTicketChannelSuffixNumber;
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Instant updatedAt;

    public String getNextTicketChannelName() {
        return "holonetbridge-%07d".formatted(nextTicketChannelSuffixNumber);
    }

    @PrePersist
    public void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
