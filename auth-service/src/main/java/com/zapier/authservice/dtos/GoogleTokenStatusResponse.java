package com.zapier.authservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleTokenStatusResponse {
    private boolean connected;
    private String email;
}
