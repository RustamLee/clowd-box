package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User login response")
public record LoginResponseDTO(
        @Schema(description = "Logged in username", example = "john_week")
        String username
) {}
