package com.zapier.authservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class GoogleTokenRefreshResult {
    private String accessToken;
    private Instant expiresAt;
}
