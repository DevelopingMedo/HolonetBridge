package de.medo.holonetbridge.commons.repository;

import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetDiscordConfigRepository extends JpaRepository<HolonetDiscordConfig, Long> {
}
