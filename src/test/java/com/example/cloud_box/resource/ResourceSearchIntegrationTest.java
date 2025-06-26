package com.example.cloud_box.resource;

import com.example.cloud_box.common.AbstractIntegrationTest;
import com.example.cloud_box.dto.LoginRequestDTO;
import com.example.cloud_box.dto.RegisterRequestDTO;
import com.example.cloud_box.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ResourceSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Cookie sessionCookieUser1;
    private Cookie sessionCookieUser2;
    private static final String USER1 = "user1";
    private static final String USER2 = "user2";
    private static final String PASS1 = "pass1";
    private static final String PASS2 = "pass2";


    @BeforeEach
    void setUp() throws Exception {
        clearMinioBucket();
        userRepository.deleteByUsername(USER1);
        userRepository.deleteByUsername(USER2);

        sessionCookieUser1 = registerAndLogin(USER1, PASS1);
        sessionCookieUser2 = registerAndLogin(USER2, PASS2);

        MockMultipartFile fileUser1 = new MockMultipartFile(
                "files", "user1_file.txt", "text/plain", "content user1".getBytes());
        mockMvc.perform(multipart("/api/resource")
                        .file(fileUser1)
                        .cookie(sessionCookieUser1)
                        .param("path", ""))
                .andExpect(status().isCreated());

        MockMultipartFile fileUser2 = new MockMultipartFile(
                "files", "user2_file.txt", "text/plain", "content user2".getBytes());
        mockMvc.perform(multipart("/api/resource")
                        .file(fileUser2)
                        .cookie(sessionCookieUser2)
                        .param("path", ""))
                .andExpect(status().isCreated());
    }

    private Cookie registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequestDTO(username, password))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(username, password))))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void userShouldFindOnlyOwnFiles() throws Exception {
        // user1 searches for user1's file
        MvcResult resultUser1 = mockMvc.perform(get("/api/resource/search")
                        .param("query", "user1_file")
                        .cookie(sessionCookieUser1))
                .andExpect(status().isOk())
                .andReturn();

        String bodyUser1 = resultUser1.getResponse().getContentAsString();
        assertTrue(bodyUser1.contains("user1_file.txt"), "User1 should see own file");
        assertFalse(bodyUser1.contains("user2_file.txt"), "User1 should NOT see user2's file");

        // user2 searches for user2's file
        MvcResult resultUser2 = mockMvc.perform(get("/api/resource/search")
                        .param("query", "user2_file")
                        .cookie(sessionCookieUser2))
                .andExpect(status().isOk())
                .andReturn();

        String bodyUser2 = resultUser2.getResponse().getContentAsString();
        assertTrue(bodyUser2.contains("user2_file.txt"), "User2 should see own file");
        assertFalse(bodyUser2.contains("user1_file.txt"), "User2 should NOT see user1's file");
    }
    void clearMinioBucket() {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(BUCKET).recursive(true).build());

            for (Result<Item> result : results) {
                deleteObjectIfExists(result.get().objectName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to clear MinIO bucket before test: " + e.getMessage());
        }
    }
    private void deleteObjectIfExists(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            System.err.println("Could not delete object: " + objectName);
        }
    }

}