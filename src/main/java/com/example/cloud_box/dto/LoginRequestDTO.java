package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;


@Schema(description = "User login request")
public record LoginRequestDTO(
        @NotBlank(message = "Username must not be blank")
        @Schema(description = "Username", example = "john_week")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Schema(description = "Password", example = "myPass123")
        String password
) {}
