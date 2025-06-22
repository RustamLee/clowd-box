package com.example.cloud_box;

import com.example.cloud_box.service.UserFolderService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestConfig {
    @Bean
    public UserFolderService userFolderService() {
        return Mockito.mock(UserFolderService.class);
    }
}
