package de.medo.holonetbridge.commons.provider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetProviderConnectionRepository extends JpaRepository<HolonetProviderConnection, UUID> {

    Optional<HolonetProviderConnection> findByGuildIdAndActiveTrue(long guildId);

    List<HolonetProviderConnection> findAllByGuildId(long guildId);
}
