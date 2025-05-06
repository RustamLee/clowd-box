package com.example.cloud_box.controller;

import com.example.cloud_box.model.User;
import com.example.cloud_box.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("sign-up")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        User user = userService.registerUser(
                registerRequest.getUsername(),
                registerRequest.getPassword(),
                registerRequest.getEmail()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getUsername()));
    }

    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;

        // геттеры и сеттеры
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


    @PostMapping("sign-in")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        User user = userService.authenticateUser(loginRequest.getUsername(), loginRequest.getPassword());
        return ResponseEntity.ok(new LoginResponse(user.getUsername()));
    }


}
