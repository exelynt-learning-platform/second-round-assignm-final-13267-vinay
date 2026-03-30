package com.ecommerce.app.config;

import com.ecommerce.app.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    // Runs every night at 02:00 AM — keeps the refresh_tokens table clean
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        log.info("Running refresh token cleanup...");
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
        log.info("Refresh token cleanup complete.");
    }
}