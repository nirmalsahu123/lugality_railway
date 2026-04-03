package com.lugality.scraper.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.api.client.http.FileContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class GoogleDriveService {

    @Value("${scraper.google-drive-folder-id:}")
    private String folderId;

    // OAuth2 credentials
    @Value("${scraper.google-client-id:}")
    private String clientId;

    @Value("${scraper.google-client-secret:}")
    private String clientSecret;

    @Value("${scraper.google-refresh-token:}")
    private String refreshToken;

    // Service account fallback
    @Value("${scraper.google-service-account-json:}")
    private String serviceAccountJson;

    private Drive driveService;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        if (folderId == null || folderId.isBlank()) {
            log.warn("⚠️ Google Drive folder ID not set — uploads disabled");
            return;
        }

        try {
            GoogleCredentials credentials;

            // Try OAuth2 first (preferred)
            if (clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank()
                    && refreshToken != null && !refreshToken.isBlank()) {

                log.info("🔐 Using OAuth2 credentials for Google Drive");
                credentials = UserCredentials.newBuilder()
                        .setClientId(clientId)
                        .setClientSecret(clientSecret)
                        .setRefreshToken(refreshToken)
                        .build();

            } else if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
                // Fallback to service account
                log.info("🔑 Using Service Account for Google Drive");
                credentials = GoogleCredentials
                        .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes()))
                        .createScoped(Collections.singletonList(DriveScopes.DRIVE));
            } else {
                log.warn("⚠️ No Google Drive credentials configured — uploads disabled");
                return;
            }

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("lugality-scraper")
                    .build();

            enabled = true;
            log.info("✅ Google Drive initialized (OAuth2). Folder: {}", folderId);

        } catch (Exception e) {
            log.error("❌ Google Drive init failed: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String uploadFile(Path localFile, String mimeType) throws IOException {
        if (!enabled) return null;
        if (!localFile.toFile().exists()) return null;

        String filename = localFile.getFileName().toString();
        String existingFileId = findFileInFolder(filename);

        File fileMetadata = new File();
        fileMetadata.setName(filename);
        FileContent mediaContent = new FileContent(mimeType, localFile.toFile());

        if (existingFileId != null) {
            driveService.files()
                    .update(existingFileId, new File(), mediaContent)
                    .setSupportsAllDrives(true)
                    .execute();
            log.info("📝 Updated on Drive: {}", filename);
            return existingFileId;
        } else {
            fileMetadata.setParents(Collections.singletonList(folderId));
            File uploaded = driveService.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .setSupportsAllDrives(true)
                    .execute();
            log.info("☁️ Uploaded to Drive: {}", filename);
            return uploaded.getId();
        }
    }

    public String uploadJson(Path jsonFile) throws IOException {
        return uploadFile(jsonFile, "application/json");
    }

    public String uploadPdf(Path pdfFile) throws IOException {
        return uploadFile(pdfFile, "application/pdf");
    }

    public String uploadCsv(Path csvFile) throws IOException {
        return uploadFile(csvFile, "text/csv");
    }

    private String findFileInFolder(String filename) {
        try {
            String query = String.format(
                    "name='%s' and '%s' in parents and trashed=false",
                    filename, folderId);
            FileList result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .execute();
            List<File> files = result.getFiles();
            return (files != null && !files.isEmpty()) ? files.get(0).getId() : null;
        } catch (Exception e) {
            log.warn("Could not check existing file {}: {}", filename, e.getMessage());
            return null;
        }
    }
}
