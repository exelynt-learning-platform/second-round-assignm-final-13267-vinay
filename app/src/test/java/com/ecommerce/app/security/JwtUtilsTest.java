package com.ecommerce.app.security;

import com.ecommerce.app.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtils")
class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private User     user;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();

        // Simulates @Value injection — must be Base64-encoded and >= 256 bits
        String secret = Base64.getEncoder().encodeToString(
                "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256!!".getBytes());
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 900_000L); // 15 min

        user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .password("hashed_pw")
                .build();
    }

    // generateToken()
    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("should return a non-null, non-blank JWT string")
        void producesNonBlankToken() {
            String token = jwtUtils.generateToken(user);
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("should produce a token with three dot-separated parts (header.payload.sig)")
        void hasThreeParts() {
            String token = jwtUtils.generateToken(user);
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("should produce different tokens on each call (due to iat claim)")
        void tokensAreUnique() throws InterruptedException {
            String t1 = jwtUtils.generateToken(user);
            Thread.sleep(1100);   // ensure different iat
            String t2 = jwtUtils.generateToken(user);
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    // extractUsername()
    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsername {

        @Test
        @DisplayName("should return the user's email as the subject")
        void returnsEmail() {
            String token = jwtUtils.generateToken(user);
            assertThat(jwtUtils.extractUsername(token)).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("should return correct email for different users")
        void multipleUsers_correctSubjects() {
            User bob = User.builder().id(2L).email("bob@example.com")
                    .password("pw").build();

            String aliceToken = jwtUtils.generateToken(user);
            String bobToken   = jwtUtils.generateToken(bob);

            assertThat(jwtUtils.extractUsername(aliceToken)).isEqualTo("alice@example.com");
            assertThat(jwtUtils.extractUsername(bobToken)).isEqualTo("bob@example.com");
        }
    }

    // isTokenValid()
    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @Test
        @DisplayName("should return true for a fresh token and its owner")
        void freshToken_validForOwner() {
            String token = jwtUtils.generateToken(user);
            assertThat(jwtUtils.isTokenValid(token, user)).isTrue();
        }

        @Test
        @DisplayName("should return false when token belongs to a different user")
        void tokenFromDifferentUser_returnsFalse() {
            String token = jwtUtils.generateToken(user);
            User bob = User.builder().id(2L).email("bob@example.com")
                    .password("pw").build();
            assertThat(jwtUtils.isTokenValid(token, bob)).isFalse();
        }
    }

    // validateToken()
    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("should return true for a valid, unexpired token")
        void validToken_returnsTrue() {
            String token = jwtUtils.generateToken(user);
            assertThat(jwtUtils.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("should return false for a completely random string")
        void randomString_returnsFalse() {
            assertThat(jwtUtils.validateToken("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("should return false for an empty string")
        void emptyString_returnsFalse() {
            assertThat(jwtUtils.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("should return false for a token signed with a different secret")
        void wrongSecret_returnsFalse() {
            // Create another JwtUtils instance with a DIFFERENT secret
            JwtUtils otherUtils = new JwtUtils();
            String otherSecret = Base64.getEncoder().encodeToString(
                    "completely-different-secret-key-for-testing-purposes!!".getBytes());
            ReflectionTestUtils.setField(otherUtils, "jwtSecret", otherSecret);
            ReflectionTestUtils.setField(otherUtils, "jwtExpirationMs", 900_000L);

            String tokenFromOtherSecret = otherUtils.generateToken(user);

            // Validate with original JwtUtils — should fail (wrong signing key)
            assertThat(jwtUtils.validateToken(tokenFromOtherSecret)).isFalse();
        }

        @Test
        @DisplayName("should return false for an expired token")
        void expiredToken_returnsFalse() {
            // Negative expiry makes token expire immediately
            ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1L);
            String expiredToken = jwtUtils.generateToken(user);
            assertThat(jwtUtils.validateToken(expiredToken)).isFalse();
        }
    }
}