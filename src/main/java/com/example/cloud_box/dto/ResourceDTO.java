package com.example.cloud_box.dto;

import com.example.cloud_box.model.ResourceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resource details, can be a file or a directory")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDTO {

    @Schema(description = "Full path to the resource", example = "folder1/file.txt")
    private String path;

    @Schema(description = "Name of the resource", example = "file.txt")
    private String name;

    @Schema(description = "Size in bytes; null for directories", example = "1024", nullable = true)
    private Long size;

    @Schema(description = "Type of resource: FILE or DIRECTORY", example = "FILE")
    private ResourceType type;

    public Long getSize() {
        return type == ResourceType.DIRECTORY ? null : size;
    }
}