package com.example.cloud_box.controller;

import com.example.cloud_box.exception.InvalidCredentialsException;
import com.example.cloud_box.exception.UnauthorizedException;
import com.example.cloud_box.model.User;
import com.example.cloud_box.service.AuthService;
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

    @PostMapping("/sign-up")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        User user = authService.registerUser(
                registerRequest.getUsername(),
                registerRequest.getPassword(),
                registerRequest.getEmail()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getUsername()));
    }


    @PostMapping("/sign-in")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
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


    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class RegisterResponse {
        private final String username;

        public RegisterResponse(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }

    public static class LoginRequest {
        private String username;
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

    public static class LoginResponse {
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
