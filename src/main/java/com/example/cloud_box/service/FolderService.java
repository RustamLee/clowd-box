package com.example.cloud_box.service;

import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.exception.*;
import com.example.cloud_box.model.ResourceType;
import com.example.cloud_box.util.ResourcePathUtils;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FolderService {

    private final MinioService minioService;

    public FolderService(MinioService minioService) {
        this.minioService = minioService;
    }

    // метод для перемещения папки, работает с нормализованными путями
    public ResourceDTO moveFolder(String from, String to) {
        if (!from.endsWith("/") || !to.endsWith("/")) {
            throw new InvalidPathException("Both source and destination paths must end with '/'");
        }

        if (minioService.resourceExists(to)) {
            throw new ResourceAlreadyExistsException("Resource already exists at destination");
        }

        boolean placeholderExists = minioService.fileExists(from);
        List<String> objects = minioService.getObjectsWithPrefix(from);

        if (objects.isEmpty() && !placeholderExists) {
            throw new ResourceNotFoundException("Directory not found: " + from);
        }

        try {
            for (String object : objects) {
                String suffix = object.substring(from.length());
                String newPath = to + suffix;

                try (InputStream in = minioService.downloadFile(object)) {
                    minioService.uploadFile(newPath, in, "application/octet-stream");
                    minioService.deleteResource(object);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to move object: " + object, e);
                }
            }

            if (placeholderExists) {
                try (InputStream in = minioService.downloadFile(from)) {
                    minioService.uploadFile(to, in, "application/x-directory");
                    if (minioService.fileExists(from)) {
                        minioService.deleteResource(from);
                    }
                } catch (Exception e) {
                    System.out.println("[FolderService.moveFolder] Warning: failed to move folder placeholder: " + from + ", " + e.getMessage());
                }
            }
            Path p = Paths.get(to.replaceAll("/$", ""));
            String name = p.getFileName().toString();
            String parent = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

            return new ResourceDTO(parent, name, null, ResourceType.DIRECTORY);
        } catch (ResourceNotFoundException | ResourceAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException("Failed to move folder", e);
        }
    }

    // метод для создания пустой папки, также работает с нормализованными путями

    public ResourceDTO createEmptyFolder(String normalizedPath) {
        System.out.println("[FolderService.createEmptyFolder] Creating empty directory at path: " + normalizedPath);

        if (minioService.resourceExists(normalizedPath)) {
            throw new ResourceAlreadyExistsException("Folder already exists: " + normalizedPath);
        }

        try {
            minioService.ensureBucketExists();
            minioService.createDirectoryPlaceholder(normalizedPath);
        } catch (MinioOperationException e) {
            throw new InternalServerException("Failed to create folder in MinIO", e);
        }


        Path p = Paths.get(normalizedPath.replaceAll("/$", ""));
        String name = p.getFileName().toString();
        String parent = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

        return new ResourceDTO(parent, name, null, ResourceType.DIRECTORY);
    }

    // метод для удаления папки, работает с нормализованными путями
    public boolean deleteFolder(String folderPath) {
        String normalizedFolderPath = ResourcePathUtils.normalizePath(folderPath, true);
        System.out.println("[FolderService.deleteFolder] normalizedFolderPath = " + normalizedFolderPath);

        boolean deleted = minioService.deleteResource(normalizedFolderPath);
        return deleted;
    }

    // метод для скачивания папки как zip-архива
    public void downloadFolderAsZip(String folderPath, HttpServletResponse response) {
        System.out.println("[FolderService.downloadFolderAsZip] Downloading folder as zip: " + folderPath);
        Iterable<Result<Item>> results = minioService.listObjects(folderPath, true);

        boolean found = false;
        List<Item> items = new ArrayList<>();

        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                if (item.objectName().equals(folderPath)) continue;
                found = true;
                items.add(item);
            } catch (Exception e) {
                throw new InternalServerException("Failed to list objects in folder: ", e);
            }
        }

        if (!found) {
            throw new ResourceNotFoundException("Folder not found or empty");
        }
        response.setContentType("application/zip");
        String zipName = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;
        response.setHeader("Content-Disposition", "attachment; filename=\"" + Paths.get(zipName).getFileName() + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Item item : items) {
                String objectName = item.objectName();
                try (InputStream inputStream = minioService.downloadFile(objectName)) {
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
        } catch (Exception e) {
            throw new InternalServerException("Failed to download folder as zip", e);
        }
    }


}
