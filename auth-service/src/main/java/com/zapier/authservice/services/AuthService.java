package com.zapier.authservice.services;

import com.zapier.authservice.dtos.AuthResponse;
import com.zapier.authservice.dtos.LoginRequest;
import com.zapier.authservice.dtos.RegisterRequest;
import com.zapier.authservice.models.User;
import com.zapier.authservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user = userRepository.save(user);

        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (user.getPassword() == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return buildResponse(user);
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getId().toString());
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUserId(user.getId().toString());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        return response;
    }

    @Value("${google.calendar.client.id}")
    private String googleClientId;

    @Value("${google.calendar.client.secret}")
    private String googleClientSecret;

    @SuppressWarnings("unchecked")
    public AuthResponse handleGoogleCallback(String code) {
        RestTemplate restTemplate = new RestTemplate();

        // Step 1 — exchange code for access token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params =
                new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri",
                "http://localhost:8085/api/auth/google/callback");
        params.add("grant_type", "authorization_code");

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token",
                new HttpEntity<>(params, headers),
                Map.class
        );

        String accessToken = (String) tokenResponse
                .getBody().get("access_token");

        // Step 2 — get user info from Google
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);

        ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        Map<String, Object> userInfo = userInfoResponse.getBody();
        String email    = (String) userInfo.get("email");
        String name     = (String) userInfo.get("name");
        String googleId = (String) userInfo.get("sub");

        // Step 3 — find or create user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name != null ? name : email);
                    newUser.setGoogleId(googleId);
                    newUser.setProvider("GOOGLE");
                    return userRepository.save(newUser);
                });

        // Step 4 — generate JWT and return
        String jwt = jwtService.generateToken(user.getId().toString());

        AuthResponse response = new AuthResponse();
        response.setToken(jwt);
        response.setUserId(user.getId().toString());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        return response;
    }
}
