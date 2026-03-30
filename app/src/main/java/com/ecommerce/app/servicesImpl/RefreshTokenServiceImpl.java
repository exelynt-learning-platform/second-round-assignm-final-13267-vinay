package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.response.AuthResponse;
import com.ecommerce.app.models.RefreshToken;
import com.ecommerce.app.models.User;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.repository.RefreshTokenRepository;
import com.ecommerce.app.security.JwtUtils;
import com.ecommerce.app.services.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils               jwtUtils;

    @Value("${jwt.refresh-expiry-ms:604800000}")   // 7 days default
    private long refreshExpiryMs;

    // Create and persist a new refresh token for a user
    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())     // opaque random token — not a JWT
                .expiresAt(Instant.now().plusMillis(refreshExpiryMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token);
    }

    // Validate the incoming refresh token, rotate it, issue a new access token
    @Override
    @Transactional
    public AuthResponse refreshAccessToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh token is missing");
        }

        RefreshToken stored = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (!stored.isValid()) {
            // Possible token reuse attack — revoke all tokens for this user
            if (stored.isRevoked()) {
                log.warn("Reuse of revoked refresh token detected for userId={}", stored.getUser().getId());
                revokeAllForUser(stored.getUser().getId());
            }
            throw new BadRequestException(
                    stored.isExpired() ? "Refresh token has expired — please log in again"
                            : "Refresh token has been revoked");
        }

        User user = stored.getUser();

        // Rotate: revoke the old token
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        // Issue new refresh token
        RefreshToken newRefreshToken = createRefreshToken(user);

        // Issue new short-lived access token
        String newAccessToken = jwtUtils.generateToken(user);

        return AuthResponse.builder()
                .token(newAccessToken)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .refreshToken(newRefreshToken.getToken())   // controller will move to cookie
                .build();
    }

    // Explicit invalidation on logout
    @Override
    @Transactional
    public void invalidate(String rawToken) {
        refreshTokenRepository.findByToken(rawToken).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
            log.info("Refresh token revoked for userId={}", t.getUser().getId());
        });
    }

    // Revoke all tokens for a user (password change, security event)
    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.warn("All refresh tokens revoked for userId={}", userId);
    }
}