package com.example.cloud_box.service;

import com.example.cloud_box.dto.ResourceDTO;
import com.example.cloud_box.exception.InternalServerException;
import com.example.cloud_box.exception.ResourceAlreadyExistsException;
import com.example.cloud_box.exception.ResourceNotFoundException;
import com.example.cloud_box.model.ResourceType;
import com.example.cloud_box.util.MimeTypes;
import com.example.cloud_box.util.ResourcePathUtils;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * It is assumed that the paths passed to methods are already normalized.
 */
@Service
public class FileService {

    private static final int BUFFER_SIZE = 1024;
    private static final String NO_SUCH_KEY_ERROR_CODE = "NoSuchKey";
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final String ATTACHMENT_FILENAME_FORMAT = "attachment; filename=\"%s\"";
    private final MinioService minioService;

    public FileService(MinioService minioService) {
        this.minioService = minioService;
    }

    public ResourceDTO move(String from, String to) {
        if (minioService.resourceExists(to)) {
            throw new ResourceAlreadyExistsException("Resource already exists at destination");
        }
        if (!minioService.fileExists(from)) {
            throw new ResourceNotFoundException("File not found: " + from);
        }
        try (InputStream in = minioService.downloadFile(from)) {
            minioService.uploadFile(to, in, MimeTypes.CONTENT_TYPE_OCTET_STREAM);
            minioService.deleteFile(from);
            StatObjectResponse stat = minioService.getFileStat(to);

            Path p = Paths.get(to);
            String name = p.getFileName().toString();
            String parent = p.getParent() != null ? p.getParent().toString().replace("\\", "/") + "/" : "";

            return new ResourceDTO(parent, name, stat.size(), ResourceType.FILE);
        } catch (
                Exception e) {
            throw new InternalServerException("Failed to move file", e);
        }
    }

    public boolean delete(String path) {
        String normalizedPath = ResourcePathUtils.normalizePath(path, false);
        return minioService.deleteResource(normalizedPath);
    }

    public void download(String path, HttpServletResponse response) {
        System.out.println("[FileService.downloadFileAsAttachment] Downloading file: " + path);
        try (InputStream inputStream = minioService.downloadFile(path)) {
            response.setContentType("application/octet-stream");
            response.setHeader(CONTENT_DISPOSITION_HEADER, ATTACHMENT_FILENAME_FORMAT + Paths.get(path).getFileName() + "\"");

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.flushBuffer();
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals(NO_SUCH_KEY_ERROR_CODE)) {
                throw new ResourceNotFoundException("File not found: " + path);
            }
            throw new InternalServerException("Minio error during file download", e);
        } catch (java.io.IOException e) {
            throw new InternalServerException("I/O error during file download", e);
        } catch (Exception e) {
            throw new InternalServerException("Unexpected error during file download", e);
        }
    }

}
