package com.ecommerce.app.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int                    status;
    private String                 error;
    private String                 message;
    private String                 path;
    private LocalDateTime          timestamp;

    // Populated only for validation errors — field → message pairs
    private Map<String, String>    validationErrors;
}