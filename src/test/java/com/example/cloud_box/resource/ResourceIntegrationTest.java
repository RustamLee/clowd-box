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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
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

    private static final String BUCKET = "cloudbox";

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2023-11-20T22-40-07Z")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin123")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());


    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.url", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin123");
        registry.add("minio.bucket", () -> BUCKET);
    }


    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteByUsername("testUser");

        RegisterRequestDTO registerRequest = new RegisterRequestDTO("testUser", "testPass123");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequestDTO loginRequest = new LoginRequestDTO("testUser", "testPass123");
        MvcResult result = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        sessionCookie = result.getResponse().getCookie("SESSION");
        clearMinio();
    }

    void clearMinio() {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(BUCKET).recursive(true).build());

            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(BUCKET)
                        .object(objectName)
                        .build());
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

        MvcResult result = mockMvc.perform(multipart("/api/resource")
                        .file(mockFile)
                        .cookie(sessionCookie)
                        .param("path", ""))
                .andExpect(status().isCreated())
                .andReturn();

        System.out.println("RESPONSE STATUS: " + result.getResponse().getStatus());
        System.out.println("RESPONSE BODY: " + result.getResponse().getContentAsString());

        User user = userRepository.findByUsername("testUser").orElseThrow();
        String userId = user.getId().toString();
        String expectedPath = "user-" + userId + "-files/" + fileName;

        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(expectedPath)
                .build());

        assertNotNull(stream, "Uploaded file should be found in MinIO");
        assertEquals("Hello, MinIO!", new String(stream.readAllBytes()));
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

        User user = userRepository.findByUsername("testUser").orElseThrow();
        String userId = user.getId().toString();
        String expectedPath = "user-" + userId + "-files/" + fileName;

        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(expectedPath)
                .build());
        assertNotNull(stream, "File should exist before deletion");
        stream.close();

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

        User user = userRepository.findByUsername("testUser").orElseThrow();
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

        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(newFilePath)
                .build());

        assertNotNull(stream);
        assertEquals("Content for rename test", new String(stream.readAllBytes()));
    }
}

