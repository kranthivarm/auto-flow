package com.zapier.authservice.repositories;

import com.zapier.authservice.models.GoogleToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleTokenRepository extends JpaRepository<GoogleToken, UUID> {
    Optional<GoogleToken> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
