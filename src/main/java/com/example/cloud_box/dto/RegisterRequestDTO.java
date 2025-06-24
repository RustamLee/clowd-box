package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;


@Schema(description = "User registration request")
public record RegisterRequestDTO(
        @NotBlank(message = "Username must not be blank")
        @Size(min = 5, max = 20, message = "Username must be between 5 and 20 characters")
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Username must contain only letters and digits")
        @Schema(description = "Username", example = "john_week")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 5, max = 20, message = "Password must be between 5 and 20 characters")
        @Schema(description = "Password", example = "myPass123")
        String password
) {
}
