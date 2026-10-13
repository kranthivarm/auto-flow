package com.zapier.authservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class GoogleTokenResponse {
    private String accessToken;
    private String userId;
    private Instant expiresAt;
}
