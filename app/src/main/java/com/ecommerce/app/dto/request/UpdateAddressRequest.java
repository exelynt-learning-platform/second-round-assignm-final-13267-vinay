package com.ecommerce.app.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * Request DTO for updating user address(es).
 */
@Data
public class UpdateAddressRequest {

    @Valid
    private AddressRequest shippingAddress;

    @Valid
    private AddressRequest billingAddress;
}
