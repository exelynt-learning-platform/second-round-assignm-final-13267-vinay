package com.ecommerce.app.services;

import com.ecommerce.app.dto.request.LoginRequest;
import com.ecommerce.app.dto.request.RegisterRequest;
import com.ecommerce.app.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}