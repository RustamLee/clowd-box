package com.example.cloud_box.controller;

import com.example.cloud_box.exception.InvalidCredentialsException;
import com.example.cloud_box.exception.UnauthorizedException;
import com.example.cloud_box.model.User;
import com.example.cloud_box.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;

import java.util.List;

@RestController
@RequestMapping("/api/auth/")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }


    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PostMapping("/sign-up")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest registerRequest) {
        User user = authService.registerUser(
                registerRequest.getUsername(),
                registerRequest.getPassword(),
                registerRequest.getEmail()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getUsername()));
    }


    @Operation(summary = "Login a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User logged in successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    @PostMapping("/sign-in")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        User user = authService.authenticateUser(loginRequest.getUsername(), loginRequest.getPassword());

        if (user == null) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        return ResponseEntity.ok(new LoginResponse(user.getUsername()));
    }


    @Schema(description = "User registration request")
    public static class RegisterRequest {
        @Schema(description = "Username", example = "john_week")
        private String username;
        @Schema(description = "Password", example = "myPass123")
        private String password;
        @Schema(description = "Email address", example = "john@example.com")
        private String email;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @Schema(description = "User registration response")
    public static class RegisterResponse {
        @Schema(description = "Registered username", example = "john_week")
        private final String username;

        public RegisterResponse(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }

    @Schema(description = "User login request")
    public static class LoginRequest {
        @Schema(description = "Username", example = "john_week")
        private String username;
        @Schema(description = "Password", example = "myPass123")
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    @Schema(description = "User login response")
    public static class LoginResponse {
        @Schema(description = "Logged in username", example = "john_week")
        private String username;

        public LoginResponse(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    @Operation(summary = "Log out the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "No active session"),
            @ApiResponse(responseCode = "500", description = "Server error during logout")
    })
    @PostMapping("/sign-out")
    public ResponseEntity<Void> logout(HttpSession session) {
        try {
            session.invalidate(); // delete the session from the Redis
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 401 Unauthorized
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

}
