package com.zapier.authservice.controllers;

import com.zapier.authservice.dtos.ApiResponse;
import com.zapier.authservice.dtos.AuthResponse;
import com.zapier.authservice.dtos.LoginRequest;
import com.zapier.authservice.dtos.RegisterRequest;
import com.zapier.authservice.services.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${google.calendar.client.id}")
    private String googleClientId;

    @Value("${google.calendar.redirect.uri}")
    private String googleRedirectUri;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @GetMapping("/google")
    public ResponseEntity<Void> googleLogin() {
        try {
            String scope = URLEncoder.encode(
                    "openid email profile",
                    StandardCharsets.UTF_8);

            // Use a separate redirect URI for plain login
            // vs calendar connect
            String loginRedirectUri = URLEncoder.encode(
                    "http://localhost:8085/api/auth/google/callback",
                    StandardCharsets.UTF_8);

            String encodedClientId = URLEncoder.encode(
                    googleClientId, StandardCharsets.UTF_8);

            String googleAuthUrl =
                    "https://accounts.google.com/o/oauth2/v2/auth" +
                            "?client_id=" + encodedClientId +
                            "&redirect_uri=" + loginRedirectUri +
                            "&response_type=code" +
                            "&scope=" + scope +
                            "&access_type=offline" +
                            "&prompt=consent";

            log.info("Redirecting to Google login");

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(googleAuthUrl))
                    .build();

        } catch (Exception e) {
            log.error("Google login redirect failed: ", e);
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Google sends user back here after login
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam String code,
            HttpServletResponse httpResponse) {
        try {
            log.info("Google OAuth callback received");
            AuthResponse response =
                    authService.handleGoogleCallback(code);

            // Redirect to frontend with token
            // Frontend JS reads token from URL and stores in localStorage
            String redirectUrl =
                    "http://localhost:5500/login.html" +
                            "?token=" + response.getToken() +
                            "&userId=" + response.getUserId() +
                            "&name=" + URLEncoder.encode(
                            response.getName(),
                            StandardCharsets.UTF_8) +
                            "&email=" + URLEncoder.encode(
                            response.getEmail(),
                            StandardCharsets.UTF_8);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();

        } catch (Exception e) {
            log.error("Google callback failed: ", e);
            String errorUrl =
                    "http://localhost:5500/login.html" +
                            "?error=" + URLEncoder.encode(
                            e.getMessage(),
                            StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(errorUrl))
                    .build();
        }
    }
}
