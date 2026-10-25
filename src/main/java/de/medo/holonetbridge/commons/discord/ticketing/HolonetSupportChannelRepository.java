package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface HolonetSupportChannelRepository extends JpaRepository<HolonetSupportChannel, Long> {

    /** The support channel with the highest sequence number in a guild, used to generate the next one. */
    Optional<HolonetSupportChannel> findTopByGuildIdOrderByNumberDesc(long guildId);
}
