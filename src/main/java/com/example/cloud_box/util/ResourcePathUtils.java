package com.example.cloud_box.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourcePathUtils {

    private ResourcePathUtils() {}

    public static String getUserRootPath(Long userId) {
        return "user-" + userId + "-files/";
    }

    public static String normalizePath(String path, Long userId) {
        if (path == null || path.isBlank()) {
            return getUserRootPath(userId);
        }

        String fixedPath = path.replace("\\", "/");
        Path nioPath = Paths.get(fixedPath).normalize();

        String normalized = nioPath.toString().replace("\\", "/");

        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        String root = getUserRootPath(userId);
        if (!normalized.startsWith(root)) {
            normalized = root + normalized;
        }

        return normalized;
    }



    public static String extractName(String objectName) {
        Path path = Paths.get(objectName).normalize();
        String name = path.getFileName().toString();
        if (objectName.endsWith("/")) {
            name += "/";
        }
        return name;
    }


    public static String extractParentPath(String objectName) {
        Path path = Paths.get(objectName).normalize();
        Path parent = path.getParent();
        return (parent != null ? parent.toString().replace("\\", "/") + "/" : "");
    }

    /**
     * Базовая нормализация без userId, но с флагом — это директория или файл.
     */
    public static String normalizePath(String path, boolean isDirectory) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null or blank");
        }

        String fixedPath = path.replace("\\", "/");
        Path nioPath = Paths.get(fixedPath).normalize();

        String normalized = nioPath.toString().replace("\\", "/");

        if (isDirectory && !normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }



}
