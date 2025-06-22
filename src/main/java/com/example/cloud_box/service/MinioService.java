package com.example.cloud_box.service;

import com.example.cloud_box.exception.MinioOperationException;
import com.example.cloud_box.util.MimeTypes;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.springframework.stereotype.Service;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.example.cloud_box.config.MinioProperties;

/**
 * It is assumed that the paths passed to methods are already normalized.
 */
@Service
public class MinioService {

    private static final String NO_SUCH_KEY_ERROR_CODE = "NoSuchKey";
    private static final int DEFAULT_PART_SIZE = 10 * 1024 * 1024; // 10MB

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.bucketName = properties.getBucket();
    }

    public void uploadFile(String objectName, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, -1, DEFAULT_PART_SIZE)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to upload file to Minio", e);
        }
    }

    public boolean fileExists(String path) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY_ERROR_CODE.equals(e.errorResponse().code())) {
                return false;
            }
            throw new MinioOperationException("MinIO error during statObject", e);
        } catch (Exception e) {
            return false;
        }
    }

    public InputStream downloadFile(String objectName) throws Exception {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to upload file to Minio", e);
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
                System.err.println("Failed to delete file: " + e.getMessage());
            }
        } else if (fileExists(path)) {
            try {
                deleteFile(path);
                resourceDeleted = true;
            } catch (MinioOperationException e) {
                System.err.println("Failed to delete file: " + e.getMessage());
            }
        }
        return resourceDeleted;
    }

    public boolean deleteFile(String path) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY_ERROR_CODE.equals(e.errorResponse().code())) {
                return false;
            }
            throw new MinioOperationException("MinIO error during removeObject", e);
        } catch (Exception e) {
            throw new MinioOperationException("Failed to delete file: " + path, e);
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

    public boolean resourceExists(String path) {
        if (directoryExists(path)) {
            return true;
        }
        return fileExists(path);
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

    public boolean directoryExists(String path) {
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
            return false;
        }
    }

    public List<String> getObjectsWithPrefix(String path) {
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

    public StatObjectResponse getFileStat(String path) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to get file stat for: " + path, e);
        }
    }

    public void createDirectoryPlaceholder(String path) {
        try {
            uploadFile(path, new ByteArrayInputStream(new byte[0]), MimeTypes.DIRECTORY);
        } catch (Exception e) {
            throw new MinioOperationException("Failed to create directory placeholder: " + path, e);
        }
    }
}
