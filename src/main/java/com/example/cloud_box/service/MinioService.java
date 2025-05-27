package com.example.cloud_box.service;

import com.example.cloud_box.model.ResourceDto;
import io.minio.*;
import io.minio.messages.Bucket;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

// This service class provides methods to upload, download, delete files, and list buckets in Minio.

public interface MinioService {


    void uploadFile(String objectName, MultipartFile file) throws Exception;

    InputStream downloadFile(String objectName) throws Exception;

    void deleteFile(String objectName) throws Exception;

    List<Bucket> listBuckets() throws Exception;

    Object getResource(String path) throws Exception;

    void deleteResource(String path);

    void downloadResource(String path, HttpServletResponse response);

    ResourceDto moveResource(String from, String to);

    void uploadFile(String objectName, InputStream inputStream, String contentType) throws Exception;

    List<ResourceDto> searchResources(String query);

    List<ResourceDto> uploadFiles(String path, List<MultipartFile> files);

    List<ResourceDto> listDirectory(String path);

    ResourceDto createDirectory(String path);

}
