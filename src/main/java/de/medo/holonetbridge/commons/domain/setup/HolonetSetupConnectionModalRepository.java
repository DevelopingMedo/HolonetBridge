package de.medo.holonetbridge.commons.domain.setup;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolonetSetupConnectionModalRepository
        extends JpaRepository<HolonetSetupConnectionModal, UUID> {}
