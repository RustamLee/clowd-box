package com.example.cloud_box.auth;

import com.example.cloud_box.dto.RegisterRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String REGISTER_URL = "/api/auth/sign-up";

    private void performInvalidRegister(RegisterRequestDTO dto) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Nested
    class UsernameValidationTests {

        @Test
        void usernameTooShort() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("abc", "validPass123");
            performInvalidRegister(dto);
        }

        @Test
        void usernameTooLong() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("a".repeat(21), "validPass123");
            performInvalidRegister(dto);
        }

        @Test
        void usernameWithInvalidCharacters() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("user_123!", "validPass123");
            performInvalidRegister(dto);
        }

        @Test
        void usernameIsBlank() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("   ", "validPass123");
            performInvalidRegister(dto);
        }
    }

    @Nested
    class PasswordValidationTests {

        @Test
        void passwordTooShort() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("validUser", "123");
            performInvalidRegister(dto);
        }

        @Test
        void passwordTooLong() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("validUser", "p".repeat(21));
            performInvalidRegister(dto);
        }

        @Test
        void passwordIsBlank() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("validUser", "   ");
            performInvalidRegister(dto);
        }
    }
}
