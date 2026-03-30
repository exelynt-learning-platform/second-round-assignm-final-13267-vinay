package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.AddressRequest;
import com.ecommerce.app.dto.request.UpdateAddressRequest;
import com.ecommerce.app.dto.response.UserProfileResponse;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.models.Address;
import com.ecommerce.app.models.User;
import com.ecommerce.app.repository.UserRepository;
import com.ecommerce.app.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = findUser(userId);
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateAddress(Long userId, UpdateAddressRequest request) {
        User user = findUser(userId);

        if (request.getShippingAddress() != null) {
            user.setShippingAddress(toAddress(request.getShippingAddress()));
        }
        if (request.getBillingAddress() != null) {
            user.setBillingAddress(toAddress(request.getBillingAddress()));
        }

        User saved = userRepository.save(user);
        return toProfileResponse(saved);
    }

    // ── Helpers

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private Address toAddress(AddressRequest req) {
        return Address.builder()
                .street(req.getStreet())
                .city(req.getCity())
                .state(req.getState())
                .zipCode(req.getZipCode())
                .country(req.getCountry())
                .build();
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .shippingAddress(toAddressResponse(user.getShippingAddress()))
                .billingAddress(toAddressResponse(user.getBillingAddress()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private UserProfileResponse.AddressResponse toAddressResponse(Address address) {
        if (address == null) return null;
        return UserProfileResponse.AddressResponse.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .build();
    }
}
