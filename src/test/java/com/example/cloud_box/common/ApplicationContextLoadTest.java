package com.example.cloud_box.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadTest extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}
}
