package com.zapier.authservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String error;
    private Instant timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setError(null);
        response.setTimestamp(Instant.now());
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setData(null);
        response.setError(message);
        response.setTimestamp(Instant.now());
        return response;
    }
}
