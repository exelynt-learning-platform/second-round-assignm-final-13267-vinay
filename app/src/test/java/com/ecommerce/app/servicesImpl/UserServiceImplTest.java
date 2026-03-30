package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.AddressRequest;
import com.ecommerce.app.dto.request.UpdateAddressRequest;
import com.ecommerce.app.dto.response.UserProfileResponse;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.models.Address;
import com.ecommerce.app.models.User;
import com.ecommerce.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .phone("1234567890")
                .build();
    }

    @Nested
    @DisplayName("getUserProfile()")
    class GetUserProfile {

        @Test
        @DisplayName("should return user profile with no addresses when none set")
        void returnsProfile_noAddresses() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            UserProfileResponse res = userService.getUserProfile(1L);

            assertThat(res.getId()).isEqualTo(1L);
            assertThat(res.getName()).isEqualTo("Alice");
            assertThat(res.getEmail()).isEqualTo("alice@example.com");
            assertThat(res.getShippingAddress()).isNull();
            assertThat(res.getBillingAddress()).isNull();
        }

        @Test
        @DisplayName("should return user profile with addresses when set")
        void returnsProfile_withAddresses() {
            user.setShippingAddress(Address.builder()
                    .street("123 Main St").city("Springfield")
                    .state("IL").zipCode("62701").country("US").build());

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            UserProfileResponse res = userService.getUserProfile(1L);

            assertThat(res.getShippingAddress()).isNotNull();
            assertThat(res.getShippingAddress().getStreet()).isEqualTo("123 Main St");
            assertThat(res.getShippingAddress().getCity()).isEqualTo("Springfield");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void userNotFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserProfile(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }
    }

    @Nested
    @DisplayName("updateAddress()")
    class UpdateAddress {

        @Test
        @DisplayName("should update shipping address only when only shipping provided")
        void updatesShippingOnly() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UpdateAddressRequest request = new UpdateAddressRequest();
            AddressRequest shipping = new AddressRequest();
            shipping.setStreet("456 Oak Ave");
            shipping.setCity("Portland");
            shipping.setState("OR");
            shipping.setZipCode("97201");
            shipping.setCountry("US");
            request.setShippingAddress(shipping);

            userService.updateAddress(1L, request);

            assertThat(user.getShippingAddress()).isNotNull();
            assertThat(user.getShippingAddress().getStreet()).isEqualTo("456 Oak Ave");
            assertThat(user.getBillingAddress()).isNull(); // not provided, should remain null
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should update both addresses when both provided")
        void updatesBoth() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UpdateAddressRequest request = new UpdateAddressRequest();

            AddressRequest shipping = new AddressRequest();
            shipping.setStreet("Ship St");
            request.setShippingAddress(shipping);

            AddressRequest billing = new AddressRequest();
            billing.setStreet("Bill St");
            request.setBillingAddress(billing);

            userService.updateAddress(1L, request);

            assertThat(user.getShippingAddress().getStreet()).isEqualTo("Ship St");
            assertThat(user.getBillingAddress().getStreet()).isEqualTo("Bill St");
        }
    }
}
