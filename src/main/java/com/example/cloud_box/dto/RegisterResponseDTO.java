package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User registration response")
public record RegisterResponseDTO(@Schema(description = "Registered username", example = "john_week") String username) {
}
