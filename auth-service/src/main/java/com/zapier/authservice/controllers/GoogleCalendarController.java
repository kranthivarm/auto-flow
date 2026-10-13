package com.zapier.authservice.controllers;

import com.zapier.authservice.dtos.ApiResponse;
import com.zapier.authservice.dtos.GoogleTokenStatusResponse;
import com.zapier.authservice.repositories.UserRepository;
import com.zapier.authservice.services.GoogleTokenService;
import com.zapier.authservice.services.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/google/calendar")
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarController {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPES =
            "openid email profile " +
            "https://www.googleapis.com/auth/calendar.readonly " +
            "https://www.googleapis.com/auth/calendar.events" +
                    "https://www.googleapis.com/auth/gmail.readonly " +
                    "https://www.googleapis.com/auth/gmail.modify";

    private final GoogleTokenService googleTokenService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${google.calendar.client.id}")
    private String clientId;

    @Value("${google.calendar.client.secret}")
    private String clientSecret;

    @Value("${google.calendar.redirect.uri}")
    private String redirectUri;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @GetMapping("/connect")
    public ResponseEntity<Void> connect(
            @RequestParam(required = false) String token,
            HttpServletRequest request) {

        UUID userId;

        // Try query param token first (browser redirect)
        // then fall back to Authorization header (API calls)
        if (token != null && !token.isBlank()) {
            try {
                String userIdStr = jwtService
                        .extractClaims(token).getSubject();
                userId = UUID.fromString(userIdStr);
            } catch (Exception e) {
                log.error("Invalid token in query param: {}",
                        e.getMessage());
                return ResponseEntity.status(
                        HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            // Fall back to Authorization header
            try {
                userId = extractUserId(request);
            } catch (Exception e) {
                log.error("No valid auth: {}", e.getMessage());
                return ResponseEntity.status(
                        HttpStatus.UNAUTHORIZED).build();
            }
        }


        String state = buildSignedState(userId.toString());

        String authUrl = GOOGLE_AUTH_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(SCOPES)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + encode(state);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .build();
    }

    @GetMapping("/callback")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> callback(
            @RequestParam String code,
            @RequestParam String state) {

        try {
            UUID userId = verifyAndExtractUserId(state);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body =
                    new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            ResponseEntity<Map> tokenResponse =
                    restTemplate.postForEntity(
                            GOOGLE_TOKEN_URL,
                            new HttpEntity<>(body, headers),
                            Map.class);

            Map<String, Object> tokenBody =
                    tokenResponse.getBody();
            String accessToken =
                    (String) tokenBody.get("access_token");
            String refreshToken =
                    (String) tokenBody.get("refresh_token");
            int expiresIn =
                    ((Number) tokenBody.get("expires_in")).intValue();
            String scope = (String) tokenBody.get("scope");

            if (refreshToken == null) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION,
                                "http://localhost:5500/connect-calendar.html" +
                                        "?error=No+refresh+token+returned." +
                                        "+Please+revoke+access+and+try+again")
                        .build();
            }

            googleTokenService.saveToken(
                    userId, accessToken, refreshToken,
                    Instant.now().plusSeconds(expiresIn), scope);

            // Redirect back to frontend with success
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION,
                            "http://localhost:5500/connect-calendar.html" +
                                    "?success=true")
                    .build();

        } catch (Exception e) {
            log.error("Calendar callback failed: ", e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION,
                            "http://localhost:5500/connect-calendar.html?success=true" +
                                    "?error=" + e.getMessage())
                    .build();
        }
    }
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<GoogleTokenStatusResponse>> status(HttpServletRequest request) {
        UUID userId = extractUserId(request);

        GoogleTokenStatusResponse response = new GoogleTokenStatusResponse();
        response.setConnected(googleTokenService.isConnected(userId));

        if (response.isConnected()) {
            userRepository.findById(userId)
                    .ifPresent(user -> response.setEmail(user.getEmail()));
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header with Bearer token required");
        }
        try {
            String token = authHeader.substring(7);
            String userId = jwtService.extractClaims(token).getSubject();
            return UUID.fromString(userId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
    }

    private String buildSignedState(String userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(userId.getBytes(StandardCharsets.UTF_8)));
            String raw = userId + "." + signature;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OAuth state", e);
        }
    }

    private UUID verifyAndExtractUserId(String state) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            int dotIndex = decoded.lastIndexOf('.');
            if (dotIndex == -1) {
                throw new IllegalArgumentException("Malformed OAuth state parameter");
            }
            String userId = decoded.substring(0, dotIndex);
            String receivedSig = decoded.substring(dotIndex + 1);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(userId.getBytes(StandardCharsets.UTF_8)));

            if (!expectedSig.equals(receivedSig)) {
                throw new IllegalArgumentException("Invalid OAuth state signature");
            }
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify OAuth state", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
