package com.example.cloud_box;

import com.example.cloud_box.dto.LoginRequestDTO;
import com.example.cloud_box.dto.RegisterRequestDTO;
import com.example.cloud_box.service.UserFolderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(TestConfig.class)
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserFolderService userFolderService;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.32")
            .withDatabaseName("cloud_box_db")
            .withUsername("clouduser")
            .withPassword("CloudUserPass2025");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.4")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }


    @Test
    void testUserRegistrationLoginAndLogout() throws Exception {
        RegisterRequestDTO registerRequest = new RegisterRequestDTO("test_user", "testPass123");

        // registration
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test_user"))
                .andExpect(cookie().exists("SESSION"));

        // registration with existing username — error 409
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict());

        // invalid login attempt with wrong password — error 401
        LoginRequestDTO wrongLogin = new LoginRequestDTO("test_user", "wrongPass");
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongLogin)))
                .andExpect(status().isUnauthorized());

        // login correctly with valid credentials
        LoginRequestDTO loginRequest = new LoginRequestDTO("test_user", "testPass123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test_user"))
                .andExpect(cookie().exists("SESSION"))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // 6. Logout — with SESSION
        mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());
    }

    @AfterEach
    void cleanupRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.flushDb();
        }
    }

    @AfterEach
    void cleanupDb() {
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 1");
    }


}