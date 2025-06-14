package com.example.cloud_box.service.impl;

import com.example.cloud_box.exception.ResourceAlreadyExistsException;
import com.example.cloud_box.exception.ResourceNotFoundException;
import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.service.MinioService;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;


    @Value("${minio.bucket}")
    private String bucketName;

    public MinioServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    @Override
    public void createUserRootFolder(String username, Long userId) {
        String bucket = "user-files";
        String folder = username + "-" + userId + "-files/";

        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(folder)
                            .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create user folder in MinIO", e);
        }
    }



    @Override
    public ResourceDTO getResource(String path) throws Exception {
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


    private ResourceDTO buildResourceDto(Item item) {
        String objectName = item.objectName();
        Path path = Paths.get(objectName);
        String name = path.getFileName().toString();
        Path parent = path.getParent();
        String parentPath = parent != null ? parent.toString().replace("\\", "/") + "/" : "";

        String type = objectName.endsWith("/") ? "DIRECTORY" : "FILE";
        Long size = objectName.endsWith("/") ? null : item.size();

        return new ResourceDTO(
                parentPath,
                name,
                size,
                type
        );
    }


    // This method deletes a resource (file or directory) from the Minio bucket.
    @Override
    public void deleteResource(String path) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (path.endsWith("/")) {
            boolean deleted = deleteFolder(path);
            if (!deleted) {
                throw new ResourceNotFoundException("Folder not found: " + path);
            }
        } else {
            boolean deleted = deleteFile(path);
            if (!deleted) {
                throw new ResourceNotFoundException("File not found: " + path);
            }
        }

    }


    public boolean deleteFile(String path) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("MinIO error during statObject", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while checking file", e);
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file: " + path, e);
        }

        return true;
    }

    public boolean deleteFolder(String folderPath) {
        List<DeleteObject> objectsToDelete = new ArrayList<>();

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(folderPath)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                objectsToDelete.add(new DeleteObject(result.get().objectName()));
            }

            // Проверка на существование объекта-папки (нулевого объекта)
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderPath)
                        .build());
                objectsToDelete.add(new DeleteObject(folderPath));
            } catch (ErrorResponseException e) {
                if (!"NoSuchKey".equals(e.errorResponse().code())) {
                    throw e;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to list folder contents", e);
        }

        if (objectsToDelete.isEmpty()) {
            return false;
        }

        try {
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucketName)
                    .objects(objectsToDelete)
                    .build());

            for (Result<DeleteError> result : results) {
                try {
                    DeleteError error = result.get();
                    throw new RuntimeException("Failed to delete object: " + error.objectName() + ", reason: " + error.message());
                } catch (Exception e) {
                    throw new RuntimeException("Error while deleting object", e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete folder: " + folderPath, e);
        }

        return true;
    }


    @Override
    public void downloadResource(String path, HttpServletResponse response) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        if (path.endsWith("/")) {
            downloadFolderAsZip(path, response);
        } else {
            downloadFileAsAttachment(path, response);
        }
    }


    private void downloadFileAsAttachment(String path, HttpServletResponse response) throws Exception {
        try (InputStream inputStream = downloadFile(path)) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + Paths.get(path).getFileName() + "\"");

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.flushBuffer();
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new ResourceNotFoundException("File not found: " + path);
            }
            throw e;
        }
    }


    private void downloadFolderAsZip(String folderPath, HttpServletResponse response) throws Exception {
        // Проверяем, что есть файлы в папке
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(folderPath)
                        .recursive(true)
                        .build()
        );

        boolean found = false;
        List<Item> items = new ArrayList<>();

        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.objectName().equals(folderPath)) continue; // пропускаем "пустую папку"
            found = true;
            items.add(item);
        }

        if (!found) {
            throw new ResourceNotFoundException("Folder not found or empty: " + folderPath);
        }
        response.setContentType("application/zip");
        String zipName = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;
        response.setHeader("Content-Disposition", "attachment; filename=\"" + Paths.get(zipName).getFileName() + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Item item : items) {
                String objectName = item.objectName();
                try (InputStream inputStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                )) {
                    String zipEntryName = objectName.substring(folderPath.length());
                    zos.putNextEntry(new ZipEntry(zipEntryName));

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }

                    zos.closeEntry();
                }
            }
            zos.finish();
        }
    }


    // This method moves a resource from one path to another in the Minio bucket.
    @Override
    public ResourceDTO moveResource(String from, String to) {
        if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
            throw new IllegalArgumentException("Source and destination paths cannot be null or empty");
        }

        if (resourceExists(to)) {
            throw new ResourceAlreadyExistsException("Resource already exists at destination: " + to); // 409
        }

        boolean isDirectory = from.endsWith("/");

        try {
            if (isDirectory) {
                List<String> objects = getObjectsWithPrefix(from);
                if (objects.isEmpty()) {
                    throw new ResourceNotFoundException("Directory not found: " + from); // 404
                }

                for (String object : objects) {
                    String newPath = to + object.substring(from.length());
                    try (InputStream in = downloadFile(object)) {
                        uploadFile(newPath, in, "application/octet-stream");
                        deleteResource(object);
                    }
                }
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(from)
                            .build());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to delete empty directory: " + from, e);
                }

                // Вернём DTO для папки
                Path p = Paths.get(to.replaceAll("/$", ""));
                String name = p.getFileName().toString();
                String parent = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

                return new ResourceDTO(parent, name, null, "DIRECTORY");

            } else {
                try (InputStream in = downloadFile(from)) {
                    uploadFile(to, in, "application/octet-stream");
                    deleteResource(from);

                    StatObjectResponse stat = minioClient.statObject(
                            StatObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(to)
                                    .build()
                    );

                    Path p = Paths.get(to);
                    String name = p.getFileName().toString();
                    String parent = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

                    return new ResourceDTO(parent, name, stat.size(), "FILE");
                }
            }

        } catch (ResourceNotFoundException | ResourceAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to move resource", e); // 500
        }
    }


    private boolean resourceExists(String path) {
        return path.endsWith("/") ? directoryExists(path) : fileExists(path);
    }

    private boolean fileExists(String path) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean directoryExists(String path) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(path.endsWith("/") ? path : path + "/")
                            .recursive(false)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.objectName().startsWith(path.endsWith("/") ? path : path + "/")) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check directory existence", e);
        }
        return false;
    }

    private List<String> getObjectsWithPrefix(String prefix) {
        List<String> objectNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : results) {
                objectNames.add(result.get().objectName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list objects with prefix: " + prefix, e);
        }
        return objectNames;
    }

















    @Override
    public void uploadFile(String objectName, MultipartFile file) throws Exception {
        ensureBucketExists();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
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
    public List<ResourceDTO> searchResources(String query) {
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

            List<ResourceDTO> matches = new ArrayList<>();
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
    public List<ResourceDTO> uploadFiles(String path, List<MultipartFile> files) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            List<ResourceDTO> uploadedResources = new ArrayList<>();
            for (MultipartFile file : files) {
                String objectName = path.endsWith("/") ? path + file.getOriginalFilename() : path + "/" + file.getOriginalFilename();
                uploadFile(objectName, file);

                Path p = Paths.get(objectName);
                String name = p.getFileName().toString();
                String parentPath = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

                uploadedResources.add(new ResourceDTO(parentPath, name, file.getSize(), "FILE"));
            }
            return uploadedResources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload files", e);
        }
    }

    // This method creates a directory in the Minio bucket.
    @Override
    public ResourceDTO createDirectory(String path) {
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

            return new ResourceDTO(parentPath, name, 0L, "DIRECTORY");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory", e);
        }
    }


    // This method lists the contents of a directory in the Minio bucket.
    @Override
    public List<ResourceDTO> listDirectory(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Проверяем, существует ли директория
        boolean exists = checkDirectoryExists(path); // реализуйте этот метод
        if (!exists) {
            throw new ResourceNotFoundException("Directory not found: " + path);
        }

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(path.endsWith("/") ? path : path + "/")
                            .delimiter("/")
                            .build()
            );

            List<ResourceDTO> resources = new ArrayList<>();
            for (Result<Item> result : results) {
                resources.add(buildResourceDto(result.get()));
            }
            return resources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list directory", e);
        }
    }

    private boolean checkDirectoryExists(String path) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(path.endsWith("/") ? path : path + "/")
                            .recursive(false)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.objectName().startsWith(path.endsWith("/") ? path : path + "/")) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check directory existence", e);
        }
        return false;
    }

    private long getResourceSize(String path) throws Exception {
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build()
        );
        return stat.size();
    }

}
