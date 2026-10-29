package de.medo.holonetbridge.commons.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

@Entity
@Data
public class HolonetBotInteractionSession {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interaction_session_generator")
    @SequenceGenerator(
            name = "interaction_session_generator",
            sequenceName = "holonet_bot_interaction_session_seq",
            allocationSize = 1)
    private Long id;
    private Locale locale;
    private Long guildId;
    private Long channelId;
    private Long memberId;
    @Setter(AccessLevel.NONE)
    private final Instant createdAt = Instant.now();
    @Setter(AccessLevel.NONE)
    private Instant updatedAt = Instant.now();
    private Instant closedAt;

    @PreUpdate
    public void update() {
        updatedAt = Instant.now();
    }
}
