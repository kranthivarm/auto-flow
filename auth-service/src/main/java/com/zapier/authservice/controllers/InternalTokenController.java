package com.zapier.authservice.controllers;

import com.zapier.authservice.dtos.ApiResponse;
import com.zapier.authservice.dtos.GoogleTokenResponse;
import com.zapier.authservice.services.GoogleTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalTokenController {

    private final GoogleTokenService googleTokenService;

    @GetMapping("/google-token/{userId}")
    public ResponseEntity<ApiResponse<GoogleTokenResponse>> getGoogleToken(
            @PathVariable UUID userId) {

        String accessToken = googleTokenService.getValidToken(userId);

        GoogleTokenResponse response = new GoogleTokenResponse();
        response.setAccessToken(accessToken);
        response.setUserId(userId.toString());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
