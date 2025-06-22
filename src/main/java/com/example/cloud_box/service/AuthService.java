package com.example.cloud_box.service;

import com.example.cloud_box.exception.UserAlreadyExistsException;
import com.example.cloud_box.model.User;
import com.example.cloud_box.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserFolderService userFolderService;
    private final AuthenticationManager authenticationManager;


    public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, UserFolderService userFolderService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userFolderService = userFolderService;
        this.authenticationManager = authenticationManager;
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

    public Authentication authenticateUser(String username, String password) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(username, password);
        Authentication authentication = authenticationManager.authenticate(authToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }
}
