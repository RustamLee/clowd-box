package com.example.cloud_box.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;


// Сервис для создания корневой папки пользователя в MinIO при регистрации нового пользователя.
@Service
public class UserFolderService {

    private final MinioService minioService;

    public UserFolderService(MinioService minioService) {
        this.minioService = minioService;
    }

    public void createUserRootFolder(Long userId) {
        String folder = "user-" + userId + "-files/";
        try {
            minioService.uploadFile(folder, new ByteArrayInputStream(new byte[0]), "application/octet-stream");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create user folder in MinIO", e);
        }
    }
}

