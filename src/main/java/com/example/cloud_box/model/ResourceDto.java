package com.example.cloud_box.model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

@Data
@NoArgsConstructor
public class ResourceDto {
    private String path;
    private String name;
    private Long size;
    private String contentType;
    private String type;        // "FILE" or "DIRECTORY"

    public ResourceDto(String path, String name, Long size, String contentType, String type) {
        this.path = path;
        this.name = name;
        this.contentType = contentType;
        this.type = type;
        this.size = size;
    }
}