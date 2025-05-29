package com.example.cloud_box.controller;

import com.example.cloud_box.model.ResourceDto;
import com.example.cloud_box.service.MinioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


@RestController
@RequiredArgsConstructor
public class MinioController {

    private final MinioService minioService;


    // get info about all resources
    @GetMapping("/resource")
    public ResponseEntity<Object> getResource(@RequestParam String path) throws Exception {
        Object resource = minioService.getResource(path);
        return ResponseEntity.ok(resource);
    }

    // delete resource
    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResource(@RequestParam String path) throws Exception {
        minioService.deleteResource(path);
        return ResponseEntity.noContent().build();
    }


    // download resource
    @GetMapping("/resource/download")
    public void download(@RequestParam String path, HttpServletResponse response) throws Exception {
        minioService.downloadResource(path, response);
    }





    // retrieve movie
    @GetMapping("/resource/move")
    public ResponseEntity<?> move(@RequestParam String from, @RequestParam String to) throws Exception {
            ResourceDto result = minioService.moveResource(from, to);
            return ResponseEntity.ok(result);
    }




    // find by name
    @GetMapping("/resource/search")
    public ResponseEntity<?> search(@RequestParam String query) {
        try {
            List<ResourceDto> results = minioService.searchResources(query);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // download file
    @PostMapping("/resource")
    public ResponseEntity<?> upload(@RequestParam String path, @RequestParam("files") List<MultipartFile> files) {
        try {
            List<ResourceDto> uploaded = minioService.uploadFiles(path, files);
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
    public ResponseEntity<?> listDirectory(@RequestParam String path) {
        try {
            List<ResourceDto> contents = minioService.listDirectory(path);
            return ResponseEntity.ok(contents);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // create directory
    @PostMapping("/directory")
    public ResponseEntity<?> createDirectory(@RequestParam String path) {
        try {
            ResourceDto folder = minioService.createDirectory(path);
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
