package com.example.cloud_box.service;

import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.exception.ResourceNotFoundException;
import com.example.cloud_box.model.ResourceType;
import com.example.cloud_box.util.ResourcePathUtils;
import com.example.cloud_box.util.SecurityUtils;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


// сервис для управления ресурсами (файлами и папками) в облачном хранилище
// все пути нормализуются здесь

@Service
public class ResourceService {
    private final FileService fileService;
    private final FolderService folderService;
    private final SecurityUtils securityUtils;
    private final MinioService minioService;

    public ResourceService(FileService fileService, FolderService folderService, SecurityUtils securityUtils, MinioService minioService) {
        this.fileService = fileService;
        this.folderService = folderService;
        this.securityUtils = securityUtils;
        this.minioService = minioService;
    }

    // метод для перемещения/ переименования ресурса (файл или папка)
    public ResourceDTO moveResource(String from, String to) throws Exception {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("Source and destination paths cannot be null or blank");
        }

        System.out.println("[ResourceService.move] Resource FROM: " + from);
        System.out.println("[ResourceService.move] Resource TO: " + to);

        ResourceType type = ResourceType.fromPath(from);
        System.out.println("[ResourceService.move] Resource type: " + type);

        Long userId = securityUtils.getCurrentUserId();
        boolean isDirectory = type == ResourceType.DIRECTORY;

        String normalizedFrom = ResourcePathUtils.normalizePath(from, userId, isDirectory);
        String normalizedTo = ResourcePathUtils.normalizePath(to, userId, isDirectory);

        System.out.println("[ResourceService.move] Resource normalized FROM: " + normalizedFrom);
        System.out.println("[ResourceService.move] Resource normalized TO: " + normalizedTo);

        return switch (type) {
            case FILE -> fileService.moveFile(normalizedFrom, normalizedTo);
            case DIRECTORY -> folderService.moveFolder(normalizedFrom, normalizedTo);
            default -> throw new IllegalArgumentException("Invalid resource type for move operation");
        };
    }


    // метод создания новой директории

    public ResourceDTO createDirectory(String path) throws Exception {
        System.out.println("[ResourceService.createDirectory] Creating directory at path: " + path);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        Long userId = securityUtils.getCurrentUserId();
        System.out.println("[ResourceService.createDirectory] Current user ID: " + userId);

        // Нормализация пути с учетом корневой директории пользователя и признака директории
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId, true);
        System.out.println("[ResourceService.createDirectory] Normalized path by iserId: " + normalizedPath);
        return folderService.createEmptyFolder(normalizedPath);
    }


    // метод для получения списка ресурсов (файло или папка ) по пути для конкретного пользователя

    public List<ResourceDTO> listDirectory(String path) {
        System.out.println("[ResourceService.listDirectory] Listing directory at path: " + path);
        Long userId = securityUtils.getCurrentUserId();
        System.out.println("[ResourceService.listDirectory] Current user ID: " + userId);
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId);
        System.out.println("[ResourceService.listDirectory] Normalized path: " + normalizedPath);
        if (!normalizedPath.endsWith("/")) {
            normalizedPath += "/";
        }
        try {
            Iterable<Result<Item>> results = minioService.listObjects(normalizedPath, false);
            List<ResourceDTO> resources = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.objectName().equals(normalizedPath)) {
                    continue;
                }
                resources.add(buildResourceDto(item));
            }
            System.out.println("[ResourceService.listDirectory] Found resources: " + resources.size());
            return resources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list directory", e);
        }
    }

//    private ResourceDTO buildResourceDto(Item item) {
//        String objectName = item.objectName();
//        ResourceType type = ResourceType.fromPath(objectName);
//        return new ResourceDTO(
//                ResourcePathUtils.extractParentPath(objectName),
//                ResourcePathUtils.extractName(objectName),
//                type == ResourceType.DIRECTORY ? null : item.size(),
//                type
//        );
//    }


    private ResourceDTO buildResourceDto(Item item) {
        Long userId = securityUtils.getCurrentUserId();
        String objectName = trimUserRootPrefix(item.objectName(), userId);
        ResourceType type = ResourceType.fromPath(objectName);

        return new ResourceDTO(
                ResourcePathUtils.extractParentPath(objectName),
                ResourcePathUtils.extractName(objectName),
                type == ResourceType.DIRECTORY ? null : item.size(),
                type
        );
    }


    private String trimUserRootPrefix(String fullPath, Long userId) {
        String prefix = "user-" + userId + "-files/";
        if (fullPath.startsWith(prefix)) {
            return fullPath.substring(prefix.length());
        }
        return fullPath;
    }


    // метод загрузки ресурса (файл или папка) в Minio
    public List<ResourceDTO> uploadResource(String path, List<MultipartFile> files) {
        Long userId = securityUtils.getCurrentUserId();

        // Нормализация пути с учётом userId
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId);

        System.out.println("[ResourceService.uploadResource] Uploading files to path: " + normalizedPath);

        if (normalizedPath == null || normalizedPath.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        if (!normalizedPath.endsWith("/")) {
            normalizedPath += "/";
        }

        try {
            List<ResourceDTO> uploadedResources = new ArrayList<>();
            for (MultipartFile file : files) {
                String objectName = normalizedPath + file.getOriginalFilename();
                uploadFile(objectName, file);
                uploadedResources.add(buildResourceDto(objectName, file.getSize()));
            }
            return uploadedResources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload files", e);
        }
    }


    public void uploadFile(String objectName, MultipartFile file) throws Exception {
        minioService.ensureBucketExists();
        minioService.uploadFile(
                objectName,
                file.getInputStream(),
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"
        );
    }

    private ResourceDTO buildResourceDto(String objectName, long size) {
        ResourceType type = ResourceType.fromPath(objectName);
        String parentPath = ResourcePathUtils.extractParentPath(objectName);
        String name = ResourcePathUtils.extractName(objectName);

        return new ResourceDTO(
                parentPath,
                name,
                type == ResourceType.DIRECTORY ? null : size,
                type
        );
    }

    // метод для удаления ресурса (файл или папка)
    public void deleteResource(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        Long userId = securityUtils.getCurrentUserId();
        ResourceType type = ResourceType.fromPath(path);
        boolean isDirectory = type == ResourceType.DIRECTORY;
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId);

        boolean deleted = isDirectory
                ? folderService.deleteFolder(normalizedPath)
                : fileService.deleteFile(normalizedPath);

        if (!deleted) {
            throw new ResourceNotFoundException((isDirectory ? "Folder" : "File") + " not found: " + path);
        }
    }

    // метод для скачивания ресурса (файл или папка)
    public void downloadResource(String path, HttpServletResponse response) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        if (path.endsWith("/")) {
            folderService.downloadFolderAsZip(path, response);
        } else {
            fileService.downloadFileAsAttachment(path, response);
        }
    }

    // метод для поиска ресурса по пути
    public List<ResourceDTO> searchResources(String query) {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }

        try {
            Iterable<Result<Item>> results = minioService.listObjects("", true);

            List<ResourceDTO> matches = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.objectName().toLowerCase().contains(query.toLowerCase())) {
                    matches.add(buildResourceDto(item));
                }
            }
            return matches;
        } catch (Exception e) {
            throw new RuntimeException("Failed to search resources", e);
        }
    }


    public ResourceDTO getResource(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        Iterable<Result<Item>> results = minioService.listObjects("", true);
        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.objectName().equals(path)) {
                return buildResourceDto(item);
            }
        }
        throw new ResourceNotFoundException("Resource not found: " + path);
    }




}
