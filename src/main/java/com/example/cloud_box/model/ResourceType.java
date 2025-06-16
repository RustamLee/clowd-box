package com.example.cloud_box.model;

public enum ResourceType {
    FILE,
    DIRECTORY;

    public static ResourceType fromPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        return path.endsWith("/") ? DIRECTORY : FILE;
    }
}
