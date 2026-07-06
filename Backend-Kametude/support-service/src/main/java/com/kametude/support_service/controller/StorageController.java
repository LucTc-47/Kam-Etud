package com.kametude.support_service.controller;

import com.kametude.support_service.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "public") String visibility,
            @RequestParam(defaultValue = "general") String category,
            @RequestParam(required = false) UUID resourceId) {
        boolean privateFile = "private".equalsIgnoreCase(visibility);
        String storedFilename = storageService.store(file, privateFile, userId, category, resourceId);
        String prefix = privateFile ? "/api/storage/private/files/" : "/api/storage/files/";
        return ResponseEntity.ok(Map.of(
                "filename", storedFilename,
                "downloadUrl", prefix + storedFilename
        ));
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws MalformedURLException {
        return fileResponse(storageService.load(filename, false), filename);
    }

    @GetMapping("/private/files/{filename}")
    public ResponseEntity<Resource> downloadPrivate(
            @PathVariable String filename,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) throws MalformedURLException {
        return fileResponse(storageService.loadPrivate(filename, userId, role), filename);
    }

    private ResponseEntity<Resource> fileResponse(Path path, String filename) throws MalformedURLException {
        Resource resource = new UrlResource(path.toUri());
        String detectedType;
        try {
            detectedType = Files.probeContentType(path);
        } catch (Exception exception) {
            detectedType = null;
        }
        return ResponseEntity.ok()
                .contentType(detectedType == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(detectedType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
