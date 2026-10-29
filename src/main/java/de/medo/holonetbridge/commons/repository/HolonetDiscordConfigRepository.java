package de.medo.holonetbridge.commons.repository;

import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface HolonetDiscordConfigRepository extends JpaRepository<HolonetDiscordConfig, Long> {

    @Query("select config.guildId from HolonetDiscordConfig config")
    List<Long> findAllGuildIds();
}
