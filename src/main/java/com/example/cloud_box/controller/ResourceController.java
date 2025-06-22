package com.example.cloud_box.controller;

import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ResourceController {

    private final ResourceService resourceService;

    // create an empty folder
    @PostMapping("/directory")
    @Operation(summary = "Create a new directory",
            description = "Creates a new directory at the specified path in the storage.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Directory created successfully",
                    content = @Content(schema = @Schema(implementation = ResourceDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path parameter",
                    content = @Content(schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "409", description = "Directory already exists or conflict",
                    content = @Content(schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(type = "string")))
    })
    public ResponseEntity<?> createDirectory(@RequestParam String path) {
        ResourceDTO folder = resourceService.createDirectory(path);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    // upload files
    @PostMapping("/resource")
    @Operation(summary = "Upload files to MinIO",
            description = "Uploads one or more files to the user's root folder in MinIO. Optionally accepts a subpath.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Files uploaded successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ResourceDTO.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid input parameters",
                    content = @Content(schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "409", description = "Conflict, e.g., file already exists",
                    content = @Content(schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(type = "string")))
    })
    public ResponseEntity<?> upload(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam MultiValueMap<String, MultipartFile> fileMap) {

        List<MultipartFile> files = fileMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<ResourceDTO> uploaded = resourceService.upload(path, files);

        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    // rename/move a resource
    @GetMapping("/resource/move")
    @Operation(
            summary = "Move or rename a resource",
            description = "Moves a file or folder from one path to another, or renames it if only the name changes."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource successfully moved or renamed"),
            @ApiResponse(responseCode = "400", description = "Invalid input: source and destination paths are required"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResourceDTO> move(
            @RequestParam String from,
            @RequestParam String to
    ) {
        ResourceDTO result = resourceService.moveResource(from, to);
        return ResponseEntity.ok(result);
    }

    // get list of resources
    @Operation(summary = "Get contents of a directory",
            description = "Returns the list of files and folders inside the specified directory path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Directory contents returned successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ResourceDTO.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid path parameter",
                    content = @Content(schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(type = "string")))
    })
    @GetMapping("/directory")
    public ResponseEntity<List<ResourceDTO>> listDirectory(@RequestParam(required = false) String path) {
        System.out.println("[MiniController.listDirectory] Called with path: " + path);
        List<ResourceDTO> contents = resourceService.listDirectory(path);
        return ResponseEntity.ok(contents);
    }

    // delete a resource
    @Operation(summary = "Delete a resource from MinIO",
            description = "Deletes the resource located at the specified path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Resource deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid path parameter"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResource(
            @Parameter(description = "Path to the resource in MinIO", required = true, example = "folder/file.txt")
            @RequestParam String path) {
        System.out.println("Delete resource controller called with path: " + path);
        resourceService.delete(path);
        return ResponseEntity.noContent().build();
    }

    // download a resource
    @GetMapping("/resource/download")
    @Operation(summary = "Download a resource from MinIO",
            description = "Streams the resource file located at the specified path to the client.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource downloaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid path parameter"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void download(
            @Parameter(description = "Path to the resource in MinIO", required = true, example = "folder/file.txt")
            @RequestParam String path, HttpServletResponse response) {
        System.out.println("[MinioController.download] Downloading resource at path: " + path);
        resourceService.download(path, response);
    }

    // find by name
    @Operation(summary = "Search resources by name",
            description = "Finds and returns a list of resources matching the search query.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ResourceDTO.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid search query",
                    content = @Content(schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(type = "string")))
    })
    @GetMapping("/resource/search")
    public ResponseEntity<List<ResourceDTO>> search(@RequestParam String query) {
        List<ResourceDTO> results = resourceService.search(query);
        return ResponseEntity.ok(results);
    }

    // get info about a resource
    @GetMapping("/resource")
    @Operation(summary = "Get information about a resource in MinIO",
            description = "Returns metadata or details about the resource located at the specified path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource information retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid path parameter"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Object> getResource(@Parameter(description = "Path to the resource in MinIO", required = true, example = "folder/file.txt")
                                              @RequestParam String path) {
        Object resource = resourceService.get(path);
        return ResponseEntity.ok(resource);
    }

}
