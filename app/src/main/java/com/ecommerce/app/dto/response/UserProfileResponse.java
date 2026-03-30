package com.ecommerce.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private Long   id;
    private String name;
    private String email;
    private String phone;

    private AddressResponse shippingAddress;
    private AddressResponse billingAddress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class AddressResponse {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }
}
