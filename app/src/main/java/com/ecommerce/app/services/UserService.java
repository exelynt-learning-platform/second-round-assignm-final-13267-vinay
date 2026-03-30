package com.ecommerce.app.services;

import com.ecommerce.app.dto.request.UpdateAddressRequest;
import com.ecommerce.app.dto.response.UserProfileResponse;

public interface UserService {

    UserProfileResponse getUserProfile(Long userId);

    UserProfileResponse updateAddress(Long userId, UpdateAddressRequest request);
}
