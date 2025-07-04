package com.example.cloud_box.controller;

import com.example.cloud_box.dto.UserProfileDTO;
import com.example.cloud_box.model.User;
import com.example.cloud_box.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final SecurityUtils securityUtils;

    public UserController(SecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileDTO.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User user = securityUtils.getCurrentUser();

        UserProfileDTO response = new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }
}
