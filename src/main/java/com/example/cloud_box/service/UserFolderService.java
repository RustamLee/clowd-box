package com.example.cloud_box.service;

import com.example.cloud_box.util.MimeTypes;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;


@Service
public class UserFolderService {

    private final MinioService minioService;

    public UserFolderService(MinioService minioService) {
        this.minioService = minioService;
    }

    public void createUserRootFolder(Long userId) {
        String folder = "user-" + userId + "-files/";
        try {
            minioService.uploadFile(folder, new ByteArrayInputStream(new byte[0]), MimeTypes.CONTENT_TYPE_OCTET_STREAM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create user folder in MinIO", e);
        }
    }
}

