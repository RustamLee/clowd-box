package com.example.cloud_box.auth;

import com.example.cloud_box.common.AbstractIntegrationTest;
import com.example.cloud_box.common.TestConfig;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public class AuthIntegrationTest extends AbstractIntegrationTest {

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

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testPass123";
    private static final String WRONG_PASSWORD = "wrongPass";


    @Test
    void testUserRegistrationLoginAndLogout() throws Exception {

        // registration
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(cookie().exists("SESSION"));

        // registration with existing username — error 409
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isConflict());

        // invalid login attempt with wrong password — error 401
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongLoginRequest())))
                .andExpect(status().isUnauthorized());

        // login correctly with valid credentials
        MvcResult loginResult = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(cookie().exists("SESSION"))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // logout
        mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());
    }


    private RegisterRequestDTO validRegisterRequest() {
        return new RegisterRequestDTO(USERNAME, PASSWORD);
    }

    private LoginRequestDTO validLoginRequest() {
        return new LoginRequestDTO(USERNAME, PASSWORD);
    }

    private LoginRequestDTO wrongLoginRequest() {
        return new LoginRequestDTO(USERNAME, WRONG_PASSWORD);
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