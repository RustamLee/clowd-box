package com.example.cloud_box.auth;

import com.example.cloud_box.common.AbstractIntegrationTest;
import com.example.cloud_box.dto.LoginRequestDTO;
import com.example.cloud_box.dto.RegisterRequestDTO;
import com.example.cloud_box.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RedisSessionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_USERNAME = "redisTestUser";
    private static final String TEST_PASSWORD = "testPass123";


    @BeforeEach
    void cleanup() {
        userRepository.deleteByUsername("redisTestUser");
    }

    @Test
    void testSessionStoredAndRemovedInRedis() throws Exception {
        // Register a new user
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isCreated());

        // Log-in the user and get the session cookie
        MvcResult loginResult = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SESSION"))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertNotNull(sessionCookie);

        // Verify that the session is stored in Redis
        String redisSessionKey = "spring:session:sessions:" + getDecodedSessionId(sessionCookie.getValue());
        assertTrue(redisTemplate.hasKey(redisSessionKey));

        // log-out the user and verify session removal
        mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());

        assertFalse(redisTemplate.hasKey(redisSessionKey));
    }

    private RegisterRequestDTO registerRequest() {
        return new RegisterRequestDTO(TEST_USERNAME, TEST_PASSWORD);
    }

    private LoginRequestDTO loginRequest() {
        return new LoginRequestDTO(TEST_USERNAME, TEST_PASSWORD);
    }

    private String getDecodedSessionId(String encodedSessionId) {
        return new String(Base64.getDecoder().decode(encodedSessionId));
    }

}
