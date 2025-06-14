package com.example.cloud_box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "User registration request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {
    @Schema(description = "Username", example = "john_week")
    private String username;
    @Schema(description = "Password", example = "myPass123")
    private String password;
}
