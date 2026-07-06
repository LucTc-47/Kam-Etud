package com.kametude.support_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import com.kametude.support_service.client.BusinessClient;
import com.kametude.support_service.entity.StoredFile;
import com.kametude.support_service.exception.SupportException;
import com.kametude.support_service.repository.StoredFileRepository;
import org.springframework.http.HttpStatus;

@Service
public class StorageService {

    private final Path rootLocation;
    private final StoredFileRepository fileRepository;
    private final BusinessClient businessClient;

    public StorageService(@Value("${app.storage.location}") String storageLocation,
                          StoredFileRepository fileRepository, BusinessClient businessClient) {
        this.rootLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        this.fileRepository = fileRepository;
        this.businessClient = businessClient;
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootLocation.resolve("public"));
            Files.createDirectories(rootLocation.resolve("private"));
        } catch (IOException e) {
            throw new RuntimeException("Impossible de creer le dossier de stockage", e);
        }
    }

    public String store(MultipartFile file, boolean privateFile, UUID ownerId,
                        String category, UUID resourceId) {
        if (file.isEmpty()) {
            throw new RuntimeException("Impossible de stocker un fichier vide");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0 && originalFilename.substring(dotIndex).matches("\\.[A-Za-z0-9]{1,10}")) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path scope = rootLocation.resolve(privateFile ? "private" : "public");
            Path destination = scope.resolve(storedFilename).normalize();
            if (!destination.startsWith(scope)) {
                throw new IllegalArgumentException("Chemin de fichier invalide");
            }
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            fileRepository.save(StoredFile.builder().filename(storedFilename).ownerId(ownerId)
                    .privateFile(privateFile).category(category).resourceId(resourceId).build());
            return storedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Echec du stockage du fichier", e);
        }
    }

    public Path load(String storedFilename) {
        // Ancien comportement conserve : lecture publique.
        return load(storedFilename, false);
    }

    public Path loadPrivate(String filename, UUID userId, String role) {
        StoredFile metadata = fileRepository.findByFilename(filename).orElse(null);
        boolean staff = "ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role);
        if (metadata == null) {
            if (!staff) throw new SupportException(HttpStatus.FORBIDDEN, "Metadonnees du fichier absentes");
        } else if (!metadata.isPrivateFile()) {
            throw new SupportException(HttpStatus.NOT_FOUND, "Fichier prive introuvable");
        } else if (!staff && !metadata.getOwnerId().equals(userId)) {
            if (metadata.getResourceId() == null
                    || !businessClient.getOrder(metadata.getResourceId()).hasParticipant(userId)) {
                throw new SupportException(HttpStatus.FORBIDDEN, "Acces au fichier refuse");
            }
        }
        return load(filename, true);
    }

    public Path load(String storedFilename, boolean privateFile) {
        Path scope = rootLocation.resolve(privateFile ? "private" : "public");
        Path file = scope.resolve(storedFilename).normalize();
        if (!file.startsWith(scope)) {
            throw new IllegalArgumentException("Chemin de fichier invalide");
        }
        return file;
    }
}
