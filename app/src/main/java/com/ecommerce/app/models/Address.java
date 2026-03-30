package com.ecommerce.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Size(max = 255)
    @Column(length = 255)
    private String street;

    @Size(max = 100)
    @Column(length = 100)
    private String city;

    @Size(max = 100)
    @Column(length = 100)
    private String state;

    @Size(max = 20)
    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Size(max = 100)
    @Column(length = 100)
    private String country;
}
