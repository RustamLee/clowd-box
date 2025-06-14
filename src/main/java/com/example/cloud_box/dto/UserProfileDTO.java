package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(description = "DTO for user profile information")
public class UserProfileDTO {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Username", example = "johndoe")
    private String username;

    @Schema(description = "Date and time when user was created", example = "2025-06-14T15:23:00")
    private LocalDateTime createdAt;

}
