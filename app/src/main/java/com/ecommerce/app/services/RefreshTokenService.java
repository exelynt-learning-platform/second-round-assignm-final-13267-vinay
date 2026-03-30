package com.ecommerce.app.services;

import com.ecommerce.app.dto.response.AuthResponse;
import com.ecommerce.app.models.RefreshToken;
import com.ecommerce.app.models.User;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(User user);
    AuthResponse refreshAccessToken(String rawToken);
    void         invalidate(String rawToken);
    void         revokeAllForUser(Long userId);
}