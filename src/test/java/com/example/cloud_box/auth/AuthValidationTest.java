package com.example.cloud_box.auth;

import com.example.cloud_box.common.AbstractIntegrationTest;
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
public class AuthValidationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String REGISTER_URL = "/api/auth/sign-up";
    private static final String VALID_USERNAME = "validUser";
    private static final String VALID_PASSWORD = "validPass123";

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
            RegisterRequestDTO dto = new RegisterRequestDTO("abc", VALID_PASSWORD);
            performInvalidRegister(dto);
        }

        @Test
        void usernameTooLong() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("a".repeat(21), VALID_PASSWORD);
            performInvalidRegister(dto);
        }

        @Test
        void usernameWithInvalidCharacters() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("user_123!", VALID_PASSWORD);
            performInvalidRegister(dto);
        }

        @Test
        void usernameIsBlank() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO("   ", VALID_PASSWORD);
            performInvalidRegister(dto);
        }
    }

    @Nested
    class PasswordValidationTests {

        @Test
        void passwordTooShort() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO(VALID_USERNAME, "123");
            performInvalidRegister(dto);
        }

        @Test
        void passwordTooLong() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO(VALID_USERNAME, "p".repeat(21));
            performInvalidRegister(dto);
        }

        @Test
        void passwordIsBlank() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO(VALID_USERNAME, "   ");
            performInvalidRegister(dto);
        }
    }
}
