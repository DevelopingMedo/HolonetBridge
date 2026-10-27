package de.medo.holonetbridge.commons.domain.ticket;

import java.util.UUID;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DiscriminatorOptions;

/**
 * A ticket value holding a title and a description ready to hand to any provider; also the persisted draft of
 * one in-progress bug/feature report, linked to the {@code HolonetBotInteractionSession} it belongs to (see
 * {@link #sessionId}) so an unfinished report survives a process restart and can be offered back to the user
 * as "resume" or "start over" (see {@code HolonetTicketSessionService} in {@code discord.ticketing}).
 *
 * <p>{@link #sessionId} is a plain foreign-key column, not a managed JPA association: nothing here ever needs
 * to navigate from a ticket back to its session object. Never add a token/credential field or a generic
 * object/JSON payload column here - every field a Discord flow collects must be named and typed explicitly,
 * like every field below.
 *
 * <p>{@code @DiscriminatorOptions(force = true)} stops Hibernate from auto-generating a {@code CHECK (kind IN
 * (...))} constraint for the {@code kind} column on schema-generated (test-only; the real schema comes from
 * Liquibase) databases - harmless in itself, but it triggers an H2 bug ("database has been closed") on every
 * insert against the in-memory databases {@code @DataJpaTest} uses.
 */
@Entity
@Table(name = "holonet_ticket")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
@DiscriminatorValue("FEATURE")
@DiscriminatorOptions(force = true)
@Getter
@SuperBuilder
public class HolonetTicket {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    private UUID sessionId;

    @NonNull private String title;

    @NonNull
    @Builder.Default
    private String description = "";

    protected HolonetTicket() {}

    /** Links this draft to the session it belongs to, once persisted (see {@code HolonetTicketSessionService}). */
    public void assignToSession(UUID sessionId) {
        this.sessionId = sessionId;
    }
}
