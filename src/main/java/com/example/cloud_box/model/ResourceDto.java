package com.example.cloud_box.model;
import com.fasterxml.jackson.annotation.JsonInclude;



@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceDto {
    private String path;
    private String name;
    private Long size;
    private String type; // "FILE" or "DIRECTORY"

    public ResourceDto(String path, String name, Long size, String type) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getSize() {
        return "DIRECTORY".equalsIgnoreCase(type) ? null : size;
    }

    public String getType() {
        return type;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ResourceDto() {
    }
}