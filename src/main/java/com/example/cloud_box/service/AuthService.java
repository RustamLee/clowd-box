package com.example.cloud_box.service;

import com.example.cloud_box.exception.InvalidCredentialsException;
import com.example.cloud_box.exception.UserAlreadyExistsException;
import com.example.cloud_box.model.User;
import com.example.cloud_box.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserFolderService userFolderService;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, UserFolderService userFolderService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userFolderService = userFolderService;
    }

    public User registerUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UserAlreadyExistsException("Username already taken");
        }

        String encodedPassword = passwordEncoder.encode(password);
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        User savedUser = userRepository.save(user);

        try {
            userFolderService.createUserRootFolder(savedUser.getId());
        } catch (Exception e) {
            System.err.println("Error creating folder in MinIO: " + e.getMessage());
        }
        return savedUser;
    }

    public User authenticateUser(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));
        System.out.println("Username: " + username);
        System.out.println("Password: " + password);
        System.out.println("Encoded: " + user.getPassword());
        System.out.println("Match: " + passwordEncoder.matches(password, user.getPassword()));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        return user;
    }

}
