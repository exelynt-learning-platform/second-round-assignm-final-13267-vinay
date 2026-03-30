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
import com.ecommerce.app.services.AuthService;
import com.ecommerce.app.services.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final CartRepository        cartRepository;
    private final PasswordEncoder       passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils              jwtUtils;
    private final RefreshTokenService   refreshTokenService;

    // Registration
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException(
                    "Email '" + request.getEmail() + "' is already registered");
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", RoleName.ROLE_USER));

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))   // bcrypt strength 12
                .phone(request.getPhone())
                .roles(Set.of(userRole))
                .build();

        user = userRepository.save(user);

        // Auto-create an empty cart for every new user
        Cart cart = Cart.builder().user(user).build();
        cartRepository.save(cart);

        String       accessToken  = jwtUtils.generateToken(user);       // 15 min JWT
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user); // 7 day DB token

        return AuthResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .refreshToken(refreshToken.getToken())   // controller moves this to HttpOnly cookie
                .build();
    }

    // Login
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        // AuthenticationManager calls CustomUserDetailsService + BCryptPasswordEncoder
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = (User) authentication.getPrincipal();

        String       accessToken  = jwtUtils.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .refreshToken(refreshToken.getToken())
                .build();
    }
}