package com.example.cloud_box.resource;

import com.example.cloud_box.common.AbstractIntegrationTest;
import com.example.cloud_box.dto.LoginRequestDTO;
import com.example.cloud_box.dto.RegisterRequestDTO;
import com.example.cloud_box.model.User;
import com.example.cloud_box.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
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
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ResourceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie sessionCookie;

    @Autowired
    private MinioClient minioClient;

    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_PASSWORD = "testPass123";

    @BeforeEach
    void setUp() throws Exception {
        clearMinioBucket();
        cleanupUser();
        sessionCookie = registerAndLoginUser(TEST_USERNAME, TEST_PASSWORD);
    }

    private void cleanupUser() {
        userRepository.deleteByUsername(TEST_USERNAME);
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

    private Cookie registerAndLoginUser(String username, String password) throws Exception {
        RegisterRequestDTO registerRequest = new RegisterRequestDTO(username, password);
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequestDTO loginRequest = new LoginRequestDTO(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getCookie("SESSION");
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

    @Test
    void testFileUpload_storesFileInMinio() throws Exception {
        String fileName = "test.txt";
        byte[] fileContent = "Hello, MinIO!".getBytes();

        MockMultipartFile mockFile = new MockMultipartFile(
                "files", fileName,
                "text/plain", fileContent
        );

        mockMvc.perform(multipart("/api/resource")
                        .file(mockFile)
                        .cookie(sessionCookie)
                        .param("path", ""))
                .andExpect(status().isCreated());

        User user = userRepository.findByUsername(TEST_USERNAME).orElseThrow();
        String expectedPath = getUserFilePath(user, fileName);

        try (InputStream stream = getFileFromMinio(expectedPath)) {
            assertNotNull(stream, "Uploaded file should be found in MinIO");
            assertEquals("Hello, MinIO!", new String(stream.readAllBytes()));
        }
    }

    private String getUserFilePath(User user, String fileName) {
        return "user-" + user.getId() + "-files/" + fileName;
    }


    private InputStream getFileFromMinio(String path) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(path)
                .build());
    }

    @Test
    void testFileDeletion_removesFromMinio() throws Exception {
        String fileName = "test_delete.txt";
        byte[] fileContent = "Will be deleted".getBytes();

        MockMultipartFile mockFile = new MockMultipartFile(
                "files", fileName,
                "text/plain", fileContent
        );

        mockMvc.perform(multipart("/api/resource")
                        .file(mockFile)
                        .cookie(sessionCookie)
                        .param("path", ""))
                .andExpect(status().isCreated());

        User user = userRepository.findByUsername(TEST_USERNAME).orElseThrow();
        String expectedPath = getUserFilePath(user, fileName);

        try (InputStream stream = getFileFromMinio(expectedPath)) {
            assertNotNull(stream, "File should exist before deletion");
        }

        mockMvc.perform(delete("/api/resource")
                        .param("path", fileName)
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());

        boolean fileStillExists;
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(expectedPath)
                    .build());
            fileStillExists = true;
        } catch (ErrorResponseException e) {
            fileStillExists = !e.errorResponse().code().equals("NoSuchKey");
        }

        assertFalse(fileStillExists, "File should not exist after deletion");
    }

    @Test
    void testMoveFileInMinio() throws Exception {
        String oldFileName = "file-to-rename.txt";
        byte[] fileContent = "Content for rename test".getBytes();

        MockMultipartFile mockFile = new MockMultipartFile(
                "files", oldFileName,
                "text/plain", fileContent
        );

        mockMvc.perform(multipart("/api/resource")
                        .file(mockFile)
                        .cookie(sessionCookie)
                        .param("path", ""))
                .andExpect(status().isCreated());

        User user = userRepository.findByUsername(TEST_USERNAME).orElseThrow();
        String userId = user.getId().toString();

        String oldFilePath = "user-" + userId + "-files/" + oldFileName;
        String newFileName = "renamed-file.txt";
        String newFilePath = "user-" + userId + "-files/" + newFileName;

        mockMvc.perform(get("/api/resource/move")
                        .cookie(sessionCookie)
                        .param("from", oldFilePath)
                        .param("to", newFilePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newFileName));

        assertThrows(ErrorResponseException.class, () -> {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(oldFilePath)
                    .build());
        });

        try (InputStream stream = getFileFromMinio(newFilePath)) {
            assertNotNull(stream);
            assertEquals("Content for rename test", new String(stream.readAllBytes()));
        }
    }
}
