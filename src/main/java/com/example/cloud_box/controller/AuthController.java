package com.example.cloud_box.controller;

import com.example.cloud_box.dto.LoginRequestDTO;
import com.example.cloud_box.dto.LoginResponseDTO;
import com.example.cloud_box.dto.RegisterRequestDTO;
import com.example.cloud_box.dto.RegisterResponseDTO;
import com.example.cloud_box.model.User;
import com.example.cloud_box.service.AuthService;
import com.example.cloud_box.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/")
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    public AuthController(AuthService authService, SecurityUtils securityUtils) {
        this.authService = authService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = RegisterResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "409", description = "Username already exists", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error during registration", content = @Content)
    })
    @PostMapping("/sign-up")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO, HttpServletRequest request) {
        User user = authService.registerUser(
                registerRequestDTO.username(),
                registerRequestDTO.password()
        );
        securityUtils.authenticateUserSession(user.getUsername(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponseDTO(user.getUsername()));
    }

    @Operation(summary = "Login a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User logged in successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server error during login", content = @Content)
    })
    @PostMapping("/sign-in")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO, HttpServletRequest request) {

        User user = authService.authenticateUser(loginRequestDTO.username(), loginRequestDTO.password());

        securityUtils.authenticateUserSession(user.getUsername(), request);

        return ResponseEntity.ok(new LoginResponseDTO(user.getUsername()));
    }

    @Operation(summary = "Log out the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "No active session"),
            @ApiResponse(responseCode = "500", description = "Server error during logout")
    })
    @PostMapping("/sign-out")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return ResponseEntity.noContent().build();
    }

}