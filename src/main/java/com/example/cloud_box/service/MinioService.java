package com.example.cloud_box.service;


import com.example.cloud_box.exception.MinioOperationException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


// сервис отвечает за низкоуровневые операции с Minio, такие как загрузка, скачивание, удаление файлов и управление ресурсами.
// все пути сюда попадают нормализованными
@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;


    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    public void uploadFile(String objectName, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, -1, 10485760)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to upload file to Minio", e);
        }
    }


    public Iterable<Result<Item>> listObjects(String prefix, boolean recursive) {
        try {
            return minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(recursive)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to list objects in Minio", e);
        }
    }


    public void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
            }
        } catch (Exception e) {
            throw new MinioOperationException("Error checking or creating bucket in Minio", e);
        }
    }


    // метод для проверки существования ресурса (файла или папки)
    public boolean resourceExists(String path) {
        System.out.println("[MinioService.resourceExists] Checking resource: " + path);
        // Проверка на наличие объектов по префиксу (даже одного)
        if (directoryExists(path)) {
            return true;
        }
        return fileExists(path);
    }

    // метод для проверки существования ФАЙЛА
    public boolean fileExists(String path) {
        System.out.println("[MinioService.fileExists] Checking file: " + path);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("MinIO error during statObject", e);
        } catch (Exception e) {
            return false;
        }
    }


    // метод для проверки существования ПАПКИ
    public boolean directoryExists(String path) {
        System.out.println("[MinioService.directoryExists] Checking directory: " + path);
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(path)
                            .maxKeys(1)
                            .recursive(true)
                            .build()
            );
            return results.iterator().hasNext();
        } catch (Exception e) {
            System.out.println("[isDirectory] Error checking: " + e.getMessage());
            return false;
        }
    }

    public InputStream downloadFile(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }


    public StatObjectResponse getFileStat(String path) throws Exception {
        System.out.println("[MinioService.getFileStat] Getting file stat: " + path);
        return minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build()
        );
    }


    public boolean deleteFile(String path) {
        System.out.println("[MinioService.deleteFile] Deleting file: " + path);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new MinioOperationException("MinIO error during removeObject", e);
        } catch (Exception e) {
            throw new MinioOperationException("Failed to delete file: " + path, e);
        }
    }

    public boolean deleteResource(String path) {
        boolean resourceDeleted = false;

        if (directoryExists(path)) {
            List<String> objects = getObjectsWithPrefix(path);
            for (String obj : objects) {
                try {
                    deleteFile(obj);
                    resourceDeleted = true;
                } catch (MinioOperationException e) {
                    System.err.println("Failed to delete object: " + obj + ", error: " + e.getMessage());
                }
            }
            try {
                if (fileExists(path)) {
                    deleteFile(path);
                    resourceDeleted = true;
                }
            } catch (MinioOperationException e) {
                System.err.println("Failed to delete directory placeholder: " + path + ", error: " + e.getMessage());
            }
        } else if (fileExists(path)) {
            try {
                deleteFile(path);
                resourceDeleted = true;
            } catch (MinioOperationException e) {
                System.err.println("Failed to delete file: " + path + ", error: " + e.getMessage());
            }
        }

        return resourceDeleted;
    }


    public List<String> getObjectsWithPrefix(String path) {
        System.out.println("[MinioService.getObjectsWithPrefix] Listing objects with prefix: " + path);
        List<String> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(path)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(item.objectName());
            }
        } catch (Exception e) {
            throw new MinioOperationException("Failed to list objects with prefix: " + path, e);
        }
        return objects;
    }

    public void createDirectoryPlaceholder(String path) {
        try {
            uploadFile(path, new ByteArrayInputStream(new byte[0]), "application/x-directory");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory placeholder: " + path, e);
        }
    }
}
