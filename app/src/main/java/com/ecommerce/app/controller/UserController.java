package com.ecommerce.app.controller;

import com.ecommerce.app.dto.request.UpdateAddressRequest;
import com.ecommerce.app.dto.response.UserProfileResponse;
import com.ecommerce.app.models.User;
import com.ecommerce.app.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/users/me — returns the authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getUserProfile(user.getId()));
    }

    /**
     * PUT /api/users/address — updates shipping and/or billing address.
     */
    @PutMapping("/address")
    public ResponseEntity<UserProfileResponse> updateAddress(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateAddressRequest request) {
        return ResponseEntity.ok(userService.updateAddress(user.getId(), request));
    }
}
