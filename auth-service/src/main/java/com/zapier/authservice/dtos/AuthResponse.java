package com.zapier.authservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthResponse {
    private String token;
    private String userId;
    private String email;
    private String name;
}
