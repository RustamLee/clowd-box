package com.example.cloud_box.common;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String BUCKET = "cloudbox";
    protected static final String MINIO_USER = "minioadmin";
    protected static final String MINIO_PASS = "minioadmin123";
    protected static final int MINIO_PORT = 9000;

    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2023-11-20T22-40-07Z";
    private static final String REDIS_IMAGE = "redis:7.2.4";
    private static final int REDIS_PORT = 6379;
    private static final String MYSQL_IMAGE = "mysql:8.0.32";
    private static final String MYSQL_DB = "cloud_box_db";
    private static final String MYSQL_USER = "clouduser";
    private static final String MYSQL_PASS = "CloudUserPass2025";

    @Container
    static GenericContainer<?> minio = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASS)
            .withCommand("server /data")
            .withExposedPorts(MINIO_PORT)
            .waitingFor(Wait.forListeningPort());

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forListeningPort());

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName(MYSQL_DB)
            .withUsername(MYSQL_USER)
            .withPassword(MYSQL_PASS);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Minio
        registry.add("minio.url", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> MINIO_USER);
        registry.add("minio.secret-key", () -> MINIO_PASS);
        registry.add("minio.bucket", () -> BUCKET);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
