package com.ecommerce.app.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;        // short-lived JWT access token (Bearer)
    private String type;         // "Bearer"
    private Long   userId;
    private String email;
    private String name;

    // Internal transport only — controller nulls this before writing to HTTP response body.
    // The actual value is placed in an HttpOnly cookie by AuthController.
    @JsonIgnore
    private String refreshToken;
}