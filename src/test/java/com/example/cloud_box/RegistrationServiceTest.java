package com.example.cloud_box;

import com.example.cloud_box.model.User;
import com.example.cloud_box.repository.UserRepository;
import com.example.cloud_box.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class RegistrationServiceTest {

    @Autowired
    private AuthService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Container
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0.32")
            .withDatabaseName("cloud_box_db")
            .withUsername("clouduser")
            .withPassword("cloudpass");

    @DynamicPropertySource
    static void setDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
    }

    @Test
    void testUserRegistration() {
        // Arrange
        User newUser = new User();
        newUser.setUsername("testuser");
        newUser.setPassword("password123");
        // Act
        registrationService.registerUser("testuser", "password123");

        // Assert
        User savedUser = userRepository.findByUsername("testuser").orElse(null);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
    }
}
