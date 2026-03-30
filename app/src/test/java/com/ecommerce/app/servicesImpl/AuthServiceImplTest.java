package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.LoginRequest;
import com.ecommerce.app.dto.request.RegisterRequest;
import com.ecommerce.app.dto.response.AuthResponse;
import com.ecommerce.app.models.Cart;
import com.ecommerce.app.models.RefreshToken;
import com.ecommerce.app.models.Role;
import com.ecommerce.app.models.User;
import com.ecommerce.app.models.enums.RoleName;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.repository.CartRepository;
import com.ecommerce.app.repository.RoleRepository;
import com.ecommerce.app.repository.UserRepository;
import com.ecommerce.app.security.JwtUtils;
import com.ecommerce.app.services.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock UserRepository        userRepository;
    @Mock RoleRepository        roleRepository;
    @Mock CartRepository        cartRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtUtils              jwtUtils;
    @Mock RefreshTokenService   refreshTokenService;

    @InjectMocks AuthServiceImpl authService;

    private Role        userRole;
    private User        savedUser;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        userRole = Role.builder()
                .id(1L)
                .name(RoleName.ROLE_USER)
                .build();

        savedUser = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .password("hashed_pw")
                .build();

        refreshToken = RefreshToken.builder()
                .id(1L)
                .token("rt-uuid-test-123")
                .user(savedUser)
                .expiresAt(Instant.now().plusSeconds(604800))
                .revoked(false)
                .build();
    }

    // register()
    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should return access token + refresh token on successful registration")
        void success_returnsTokens() {
            RegisterRequest req = buildRegisterRequest(
                    "Alice", "alice@example.com", "Password1!", "9999999999");

            when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
            when(passwordEncoder.encode(req.getPassword())).thenReturn("hashed_pw");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(cartRepository.save(any(Cart.class))).thenReturn(new Cart());
            when(jwtUtils.generateToken(any(User.class))).thenReturn("access-jwt");
            when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken);

            AuthResponse res = authService.register(req);

            assertThat(res.getToken()).isEqualTo("access-jwt");
            assertThat(res.getType()).isEqualTo("Bearer");
            assertThat(res.getEmail()).isEqualTo("alice@example.com");
            assertThat(res.getRefreshToken()).isEqualTo("rt-uuid-test-123");
            assertThat(res.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should hash password using bcrypt before saving")
        void success_passwordIsEncoded() {
            RegisterRequest req = buildRegisterRequest(
                    "Alice", "alice@example.com", "Password1!", null);

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
            when(passwordEncoder.encode("Password1!")).thenReturn("$2a$12$hashedpw");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(cartRepository.save(any(Cart.class))).thenReturn(new Cart());
            when(jwtUtils.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn(refreshToken);

            authService.register(req);

            // Verify bcrypt was called with raw password
            verify(passwordEncoder).encode("Password1!");
            // Verify raw password was never saved directly
            verify(userRepository).save(argThat(u ->
                    !u.getPassword().equals("Password1!")));
        }

        @Test
        @DisplayName("should auto-create empty cart for the new user")
        void success_cartIsCreated() {
            RegisterRequest req = buildRegisterRequest(
                    "Alice", "alice@example.com", "Password1!", null);

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any())).thenReturn(savedUser);
            when(cartRepository.save(any(Cart.class))).thenReturn(new Cart());
            when(jwtUtils.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn(refreshToken);

            authService.register(req);

            verify(cartRepository, times(1)).save(any(Cart.class));
        }

        @Test
        @DisplayName("should throw BadRequestException when email already exists")
        void duplicateEmail_throwsBadRequest() {
            RegisterRequest req = buildRegisterRequest(
                    "Alice", "alice@example.com", "Password1!", null);

            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already registered");

            verify(userRepository, never()).save(any());
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when ROLE_USER not seeded")
        void missingRole_throwsNotFound() {
            RegisterRequest req = buildRegisterRequest(
                    "Alice", "alice@example.com", "Password1!", null);

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.register(req))
                    .hasMessageContaining("Role");

            verify(userRepository, never()).save(any());
        }
    }

    // login()
    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return access token and refresh token on valid credentials")
        void success_returnsTokens() {
            LoginRequest req = buildLoginRequest("alice@example.com", "Password1!");

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            savedUser, null, savedUser.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authToken);
            when(jwtUtils.generateToken(savedUser)).thenReturn("access-jwt");
            when(refreshTokenService.createRefreshToken(savedUser)).thenReturn(refreshToken);

            AuthResponse res = authService.login(req);

            assertThat(res.getToken()).isEqualTo("access-jwt");
            assertThat(res.getRefreshToken()).isEqualTo("rt-uuid-test-123");
            assertThat(res.getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("should propagate BadCredentialsException on wrong password")
        void wrongPassword_throwsBadCredentials() {
            LoginRequest req = buildLoginRequest("alice@example.com", "wrongpass");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class);

            verify(jwtUtils, never()).generateToken(any());
        }

        @Test
        @DisplayName("should authenticate using email as username (not userId)")
        void login_usesEmailAsUsername() {
            LoginRequest req = buildLoginRequest("alice@example.com", "Password1!");

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            savedUser, null, savedUser.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authToken);
            when(jwtUtils.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn(refreshToken);

            authService.login(req);

            verify(authenticationManager).authenticate(
                    argThat(a -> {
                        UsernamePasswordAuthenticationToken t =
                                (UsernamePasswordAuthenticationToken) a;
                        return "alice@example.com".equals(t.getPrincipal());
                    }));
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────
    private RegisterRequest buildRegisterRequest(
            String name, String email, String password, String phone) {
        RegisterRequest r = new RegisterRequest();
        r.setName(name); r.setEmail(email);
        r.setPassword(password); r.setPhone(phone);
        return r;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email); r.setPassword(password);
        return r;
    }
}