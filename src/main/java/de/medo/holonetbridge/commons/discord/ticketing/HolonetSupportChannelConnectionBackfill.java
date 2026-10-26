package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time (per row) migration for support channels created before {@code connectionId} existed on
 * {@link HolonetSupportChannel}: such a row has {@code ticketId} set but no {@code connectionId} to pair it
 * with. A row can only be auto-linked if its guild has exactly one {@code HolonetProviderConnection} - an
 * unambiguous candidate it must have been created against; a guild with zero or more than one candidate is
 * left unresolved and logged instead of guessed at, since silently picking the wrong connection would let a
 * future lookup resolve the wrong provider for that ticket. Runs on every startup; idempotent once every
 * resolvable row has been linked (the query that drives it only ever finds the ones still missing a link).
 */
@Slf4j
@Component
class HolonetSupportChannelConnectionBackfill implements ApplicationRunner {

    private final HolonetSupportChannelRepository supportChannelRepository;
    private final HolonetProviderConnectionRepository providerConnectionRepository;

    HolonetSupportChannelConnectionBackfill(
            HolonetSupportChannelRepository supportChannelRepository,
            HolonetProviderConnectionRepository providerConnectionRepository) {
        this.supportChannelRepository = supportChannelRepository;
        this.providerConnectionRepository = providerConnectionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    @Transactional
    void backfill() {
        List<HolonetSupportChannel> unresolved = supportChannelRepository.findByConnectionIdIsNullAndTicketIdIsNotNull();
        if (unresolved.isEmpty()) {
            return;
        }
        Map<Long, List<HolonetSupportChannel>> byGuild =
                unresolved.stream().collect(Collectors.groupingBy(HolonetSupportChannel::guildId));
        byGuild.forEach(this::linkIfUnambiguous);
    }

    private void linkIfUnambiguous(long guildId, List<HolonetSupportChannel> channels) {
        List<HolonetProviderConnection> candidates = providerConnectionRepository.findAllByGuildId(guildId);
        if (candidates.size() != 1) {
            log.warn(
                    "Cannot backfill connectionId for {} support channel(s) of guildId={}: {} candidate "
                            + "connection(s) found (need exactly 1). Leaving unresolved.",
                    channels.size(),
                    guildId,
                    candidates.size());
            return;
        }
        UUID connectionId = candidates.get(0).id();
        channels.forEach(channel -> channel.linkLegacyConnection(connectionId));
        supportChannelRepository.saveAll(channels);
    }
}
