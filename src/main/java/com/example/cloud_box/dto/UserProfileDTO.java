package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "DTO for user profile information")
public record UserProfileDTO(
        @Schema(description = "User ID", example = "1")
        Long id,

        @Schema(description = "Username", example = "johndoe")
        String username,

        @Schema(description = "Date and time when user was created", example = "2025-06-14T15:23:00")
        LocalDateTime createdAt
) {}
