package com.example.cloud_box.controller;

import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.model.User;
import com.example.cloud_box.repository.UserRepository;
import com.example.cloud_box.service.MinioService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MinioController {

    private final MinioService minioService;
    private final UserRepository userRepository;



    // get info about all resources
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
                                              @RequestParam String path) throws Exception {
        Object resource = minioService.getResource(path);
        return ResponseEntity.ok(resource);
    }

    // delete resource
    @DeleteMapping("/resource")
    @Operation(summary = "Delete a resource from MinIO",
            description = "Deletes the resource located at the specified path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Resource deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid path parameter"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteResource(
            @Parameter(description = "Path to the resource in MinIO", required = true, example = "folder/file.txt")
            @RequestParam String path) throws Exception {
        minioService.deleteResource(path);
        return ResponseEntity.noContent().build();
    }


    // download resource
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
            @RequestParam String path, HttpServletResponse response) throws Exception {
        minioService.downloadResource(path, response);
    }


    // retrieve movie
    @GetMapping("/resource/move")
    @Operation(summary = "Move a resource within MinIO",
            description = "Moves a resource from one path to another.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource moved successfully",
                    content = @Content(schema = @Schema(implementation = ResourceDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path parameters"),
            @ApiResponse(responseCode = "404", description = "Source resource not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> move(
            @Parameter(description = "Current path of the resource", required = true, example = "folder/oldName.txt")
            @RequestParam String from,
            @Parameter(description = "New path for the resource", required = true, example = "folder/newName.txt")
            @RequestParam String to) throws Exception {
        ResourceDTO result = minioService.moveResource(from, to);
        return ResponseEntity.ok(result);
    }


    // find by name
    @GetMapping("/resource/search")
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
    public ResponseEntity<?> search(
            @Parameter(description = "Search query string", required = true, example = "myfile")
            @RequestParam String query) {
        try {
            List<ResourceDTO> results = minioService.searchResources(query);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // download file
    @PostMapping("/resource")
    @Operation(summary = "Upload files to MinIO",
            description = "Uploads one or more files to the specified path in MinIO.")
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
            @Parameter(description = "Target path to upload files", required = true, example = "folder/subfolder")
            @RequestParam String path,
            @Parameter(description = "List of files to upload", required = true)
            @RequestParam("files") List<MultipartFile> files) {
        try {
            List<ResourceDTO> uploaded = minioService.uploadFiles(path, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // get content of file
    @GetMapping("/directory")
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
    public ResponseEntity<?> listDirectory(@RequestParam(required = false) String path, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
            }

            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (path == null || path.isBlank()) {
                path = user.getUsername()+ "-"+user.getId() + "-files/";
            }

            List<ResourceDTO> contents = minioService.listDirectory(path);
            return ResponseEntity.ok(contents);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace(); // Это напечатает стек ошибки в консоль IDE

            return ResponseEntity.status(500).body("Internal server error");
        }

    }

    // create directory
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

    public ResponseEntity<?> createDirectory(
            @Parameter(description = "Path where the new directory will be created", required = true, example = "folder/newDir")
            @RequestParam String path) {
        try {
            ResourceDTO folder = minioService.createDirectory(path);
            return ResponseEntity.status(HttpStatus.CREATED).body(folder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

}
