package com.example.cloud_box.service;

import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.exception.*;
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

    // метод создания новой директории

    public ResourceDTO createDirectory(String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidPathException("Path cannot be null or empty");
        }

        Long userId = securityUtils.getCurrentUserId();
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId, true);
        return folderService.createEmptyFolder(normalizedPath);
    }


    public void uploadFile(String objectName, MultipartFile file) throws Exception {
        minioService.ensureBucketExists();
        minioService.uploadFile(
                objectName,
                file.getInputStream(),
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"
        );
    }


    // метод для перемещения/ переименования ресурса (файл или папка)
    public ResourceDTO moveResource(String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new InvalidPathException("Source and destination paths cannot be null or blank");
        }

        ResourceType type = ResourceType.fromPath(from);

        Long userId = securityUtils.getCurrentUserId();
        boolean isDirectory = type == ResourceType.DIRECTORY;

        String normalizedFrom = ResourcePathUtils.normalizePath(from, userId, isDirectory);
        String normalizedTo = ResourcePathUtils.normalizePath(to, userId, isDirectory);

        return switch (type) {
            case FILE -> fileService.moveFile(normalizedFrom, normalizedTo);
            case DIRECTORY -> folderService.moveFolder(normalizedFrom, normalizedTo);
            default -> throw new InvalidPathException("Invalid resource type for move operation");
        };
    }

    // метод для получения списка ресурсов (файло или папка ) по пути для конкретного пользователя
    public List<ResourceDTO> listDirectory(String path) {
        Long userId = securityUtils.getCurrentUserId();
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId);
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
            return resources;
        } catch (Exception e) {
            throw new InternalServerException("Failed to list directory", e);
        }
    }

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

        if (files == null || files.isEmpty()) {
            throw new InvalidInputException("No files provided for upload.");
        }

        String normalizedPath = ResourcePathUtils.normalizePath(path, userId);

        if (normalizedPath == null || normalizedPath.isEmpty()) {
            throw new InvalidPathException("Path cannot be null or empty");
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
        } catch (IOException e) {
            throw new InternalServerException("Failed to read file data for upload.", e);
        } catch (MinioOperationException e) {
            throw new InternalServerException("Failed to upload files to storage.", e);
        } catch (Exception e) {
            throw new InternalServerException("An unexpected error occurred during file upload.", e);
        }
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
            throw new InvalidPathException("Path cannot be null or empty");
        }
        Long userId = securityUtils.getCurrentUserId();
        ResourceType type = ResourceType.fromPath(path);
        boolean isDirectory = type == ResourceType.DIRECTORY;
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId);

        boolean deleted = isDirectory
                ? folderService.deleteFolder(normalizedPath)
                : fileService.deleteFile(normalizedPath);

        if (!deleted) {
            throw new ResourceNotFoundException((isDirectory ? "Folder" : "File") + " not found");
        }
    }

    // метод для скачивания ресурса (файл или папка)
    public void downloadResource(String path, HttpServletResponse response) {
        Long userId = securityUtils.getCurrentUserId();
        ResourceType type = ResourceType.fromPath(path);
        boolean isDirectory = type == ResourceType.DIRECTORY;
        if (path == null || path.trim().isEmpty()) {
            throw new InvalidPathException("Path cannot be null or empty");
        }
        String normalizedPath = ResourcePathUtils.normalizePath(path, userId, isDirectory);
        System.out.println("[ResourceService.downloadResource] Normalized path");
        if (isDirectory) {
            folderService.downloadFolderAsZip(normalizedPath, response);
        } else {
            fileService.downloadFileAsAttachment(normalizedPath, response);
        }

    }

    // метод для поиска ресурса по пути
    public List<ResourceDTO> searchResources(String query) {
        if (query == null || query.isEmpty()) {
            throw new InvalidQueryException("Query cannot be null or empty");
        }

        try {
            Iterable<Result<Item>> results = minioService.listObjects("", true);

            List<ResourceDTO> matches = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get(); // тут может Exception
                if (item.objectName().toLowerCase().contains(query.toLowerCase())) {
                    matches.add(buildResourceDto(item));
                }
            }
            return matches;
        } catch (Exception e) {
            throw new MinioOperationException("Failed to search resources", e);
        }
    }


    public ResourceDTO getResource(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        Iterable<Result<Item>> results = minioService.listObjects("", true);
        try {
            for (Result<Item> result : results) {
                Item item = result.get(); // тут может Exception
                if (item.objectName().equals(path)) {
                    return buildResourceDto(item);
                }
            }
            throw new ResourceNotFoundException("Resource not found");
        } catch (Exception e) {
            throw new MinioOperationException("Failed to retrieve resource from MinIO", e);
        }
    }
}
