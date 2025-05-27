package com.example.cloud_box.service.impl;

import com.example.cloud_box.exception.ResourceNotFoundException;
import com.example.cloud_box.model.ResourceDto;
import com.example.cloud_box.service.MinioService;
import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;


    @Value("${minio.bucket}")
    private String bucketName;

    public MinioServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    @Override
    public ResourceDto getResource(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(path)
                        .recursive(false)
                        .build()
        );
        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.objectName().equals(path)) {
                return buildResourceDto(item);
            }
        }
        throw new ResourceNotFoundException("Resource not found: " + path);
    }

    private ResourceDto buildResourceDto(Item item) {
        String objectName = item.objectName();
        Path path = Paths.get(objectName);
        String name = path.getFileName().toString();
        Path parent = path.getParent();
        String parentPath = parent != null ? parent.toString().replace("\\", "/") + "/" : "";

        String type = objectName.endsWith("/") ? "DIRECTORY" : "FILE";
        Long size = objectName.endsWith("/") ? null : item.size();
        String contentType = "application/octet-stream";
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(item.objectName()).build()
            );
            if (stat != null && stat.contentType() != null) {
                contentType = stat.contentType();
            }
        } catch (Exception e) {
        }
        return new ResourceDto(
                parentPath,
                name,
                size,
                contentType,
                type
        );
    }


    @Override
    public void uploadFile(String objectName, MultipartFile file) throws Exception {
        ensureBucketExists();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
    }

    @Override
    public InputStream downloadFile(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }


    @Override
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    private void ensureBucketExists() throws Exception {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!found) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
        }
    }


    @Override
    public List<Bucket> listBuckets() throws Exception {
        return minioClient.listBuckets();
    }

    @Override
    public void deleteResource(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            deleteFile(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete resource", e);
        }
    }

    // This method downloads a resource from the Minio bucket and writes it to the HttpServletResponse.
    @Override
    public void downloadResource(String path, HttpServletResponse response) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try (InputStream inputStream = downloadFile(path)) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + Paths.get(path).getFileName() + "\"");
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download resource", e);
        }
    }

    // This method moves a resource from one path to another in the Minio bucket.
    @Override
    public ResourceDto moveResource(String from, String to) {
        if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
            throw new IllegalArgumentException("Source and destination paths cannot be null or empty");
        }

        try (InputStream inputStream = downloadFile(from)) {
            uploadFile(to, inputStream, "application/octet-stream");
            deleteResource(from);
            // Возвращаем новый ResourceDto
            Path p = Paths.get(to);
            String name = p.getFileName().toString();
            String parentPath = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";
            return new ResourceDto(parentPath, name, 0L, "application/octet-stream", "FILE");
        } catch (Exception e) {
            throw new RuntimeException("Failed to move resource", e);
        }
    }

    // This method uploads a file to the Minio bucket using an InputStream.
    @Override
    public void uploadFile(String objectName, InputStream inputStream, String contentType) throws Exception {
        ensureBucketExists();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, -1, 10485760)
                        .contentType(contentType)
                        .build()
        );
    }

    // this method searches for resources in the Minio bucket based on a query string.
    @Override
    public List<ResourceDto> searchResources(String query) {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .recursive(true)
                            .build()
            );

            List<ResourceDto> matches = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.objectName().contains(query)) {
                    matches.add(buildResourceDto(item));
                }
            }
            return matches;
        } catch (Exception e) {
            throw new RuntimeException("Failed to search resources", e);
        }
    }


    // This method uploads multiple files to a specified path in the Minio bucket.
    @Override
    public List<ResourceDto> uploadFiles(String path, List<MultipartFile> files) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            List<ResourceDto> uploadedResources = new ArrayList<>();
            for (MultipartFile file : files) {
                String objectName = path.endsWith("/") ? path + file.getOriginalFilename() : path + "/" + file.getOriginalFilename();
                uploadFile(objectName, file);

                Path p = Paths.get(objectName);
                String name = p.getFileName().toString();
                String parentPath = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

                uploadedResources.add(new ResourceDto(parentPath, name, file.getSize(), file.getContentType(), "FILE"));
            }
            return uploadedResources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload files", e);
        }
    }

    // This method creates a directory in the Minio bucket.
    @Override
    public ResourceDto createDirectory(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            ensureBucketExists();
            String dirPath = path.endsWith("/") ? path : path + "/";
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(dirPath)
                            .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .contentType("application/x-directory")
                            .build()
            );

            Path p = Paths.get(dirPath);
            String name = p.getFileName().toString();
            String parentPath = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

            return new ResourceDto(parentPath, name, 0L, "application/x-directory", "DIRECTORY");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory", e);
        }
    }


    // This method lists the contents of a directory in the Minio bucket.
    @Override
    public List<ResourceDto> listDirectory(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(path.endsWith("/") ? path : path + "/")
                            .delimiter("/")
                            .build()
            );

            List<ResourceDto> resources = new ArrayList<>();
            for (Result<Item> result : results) {
                resources.add(buildResourceDto(result.get()));
            }
            return resources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list directory", e);
        }
    }

}
