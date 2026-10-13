package com.zapier.authservice.services;

import com.zapier.authservice.dtos.GoogleTokenRefreshResult;
import com.zapier.authservice.models.GoogleToken;
import com.zapier.authservice.repositories.GoogleTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleTokenService {

    private final GoogleTokenRepository googleTokenRepository;
    private final EncryptionService encryptionService;
    private final RestTemplate restTemplate;

    @Value("${google.calendar.client.id}")
    private String clientId;

    @Value("${google.calendar.client.secret}")
    private String clientSecret;

    @Value("${google.calendar.token.endpoint}")
    private String tokenEndpoint;

    @Transactional
    public void saveToken(UUID userId, String accessToken, String refreshToken,
                          Instant expiresAt, String scope) {
        GoogleToken token = googleTokenRepository.findByUserId(userId)
                .orElse(new GoogleToken());
        token.setUserId(userId);
        token.setAccessToken(encryptionService.encrypt(accessToken));
        token.setRefreshToken(encryptionService.encrypt(refreshToken));
        token.setExpiresAt(expiresAt);
        token.setScope(scope != null ? scope : "");
        googleTokenRepository.save(token);
    }

    @Transactional
    public String getValidToken(UUID userId) {
        GoogleToken token = googleTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Google Calendar not connected for this user"));

        if (isExpiringSoon(token.getExpiresAt())) {
            return refreshAndSave(token);
        }
        return encryptionService.decrypt(token.getAccessToken());
    }

    public GoogleTokenRefreshResult refreshAccessToken(String refreshToken) {
        return callGoogleRefreshEndpoint(refreshToken);
    }

    @Transactional
    public void revokeToken(UUID userId) {
        GoogleToken token = googleTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Google Calendar not connected for this user"));

        try {
            String accessToken = encryptionService.decrypt(token.getAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", accessToken);
            restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/revoke",
                    new HttpEntity<>(body, headers),
                    Void.class);
        } catch (Exception ignored) {
            // Revoke is best-effort; token may already be expired or invalid
        }

        googleTokenRepository.deleteByUserId(userId);
    }

    public boolean isConnected(UUID userId) {
        return googleTokenRepository.findByUserId(userId).isPresent();
    }

    private boolean isExpiringSoon(Instant expiresAt) {
        // Refresh if token expires within the next 5 minutes
        return Instant.now().isAfter(expiresAt.minusSeconds(300));
    }

    private String refreshAndSave(GoogleToken token) {
        String refreshToken = encryptionService.decrypt(token.getRefreshToken());
        GoogleTokenRefreshResult result = callGoogleRefreshEndpoint(refreshToken);
        token.setAccessToken(encryptionService.encrypt(result.getAccessToken()));
        token.setExpiresAt(result.getExpiresAt());
        googleTokenRepository.save(token);
        return result.getAccessToken();
    }

    @SuppressWarnings("unchecked")
    private GoogleTokenRefreshResult callGoogleRefreshEndpoint(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenEndpoint,
                new HttpEntity<>(body, headers),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to refresh Google access token");
        }

        Map<String, Object> responseBody = response.getBody();
        String newAccessToken = (String) responseBody.get("access_token");
        int expiresIn = ((Number) responseBody.get("expires_in")).intValue();

        GoogleTokenRefreshResult result = new GoogleTokenRefreshResult();
        result.setAccessToken(newAccessToken);
        result.setExpiresAt(Instant.now().plusSeconds(expiresIn));
        return result;
    }
}
