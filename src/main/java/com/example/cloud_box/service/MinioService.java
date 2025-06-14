package com.example.cloud_box.service;

import com.example.cloud_box.dto.ResourceDTO;
import io.minio.messages.Bucket;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import io.minio.errors.MinioException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.io.IOException;
import java.security.InvalidKeyException;


// This service class provides methods to upload, download, delete files, and list buckets in Minio.

public interface MinioService {


    void createUserRootFolder (String username,Long userId) throws Exception;

    void uploadFile(String objectName, MultipartFile file) throws Exception;

    InputStream downloadFile(String objectName) throws Exception;

    boolean deleteFile(String objectName) throws Exception;

    List<Bucket> listBuckets() throws Exception;

    Object getResource(String path) throws Exception;


    void deleteResource(String path) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException;


    void downloadResource(String path, HttpServletResponse response) throws Exception;

    ResourceDTO moveResource(String from, String to);

    void uploadFile(String objectName, InputStream inputStream, String contentType) throws Exception;

    List<ResourceDTO> searchResources(String query);

    List<ResourceDTO> uploadFiles(String path, List<MultipartFile> files);

    List<ResourceDTO> listDirectory(String path);

    ResourceDTO createDirectory(String path);

}
