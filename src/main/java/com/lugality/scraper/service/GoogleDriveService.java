package com.lugality.scraper.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.http.FileContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Google Drive upload service.
 * Uploads scraped JSON, PDF, and CSV files to a shared Google Drive folder.
 *
 * Setup:
 *   1. Create a Google Cloud Service Account
 *   2. Enable Google Drive API
 *   3. Share your Drive folder with the service account email
 *   4. Set GOOGLE_SERVICE_ACCOUNT_JSON and GOOGLE_DRIVE_FOLDER_ID in Railway variables
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "scraper.storage-mode", havingValue = "google_drive")
public class GoogleDriveService {

    @Value("${scraper.google-drive-folder-id}")
    private String folderId;

    @Value("${scraper.google-service-account-json}")
    private String serviceAccountJson;

    private Drive driveService;

    private static final List<String> SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/drive.file");

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes()))
                    .createScoped(SCOPES);

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("lugality-scraper")
                    .build();

            log.info("✅ Google Drive service initialized. Folder: {}", folderId);
        } catch (Exception e) {
            log.error("❌ Failed to initialize Google Drive service: {}", e.getMessage(), e);
            throw new RuntimeException("Google Drive init failed", e);
        }
    }

    /**
     * Upload a file to Google Drive folder.
     * If a file with the same name already exists, it will be updated.
     */
    public String uploadFile(Path localFile, String mimeType) throws IOException {
        String filename = localFile.getFileName().toString();

        // Check if file already exists → update instead of duplicate
        String existingFileId = findFileInFolder(filename);

        File fileMetadata = new File();
        fileMetadata.setName(filename);

        FileContent mediaContent = new FileContent(mimeType, localFile.toFile());

        if (existingFileId != null) {
            // Update existing file
            driveService.files().update(existingFileId, new File(), mediaContent).execute();
            log.info("📝 Updated on Drive: {}", filename);
            return existingFileId;
        } else {
            // Create new file
            fileMetadata.setParents(Collections.singletonList(folderId));
            File uploadedFile = driveService.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute();
            log.info("☁️ Uploaded to Drive: {} (id={})", filename, uploadedFile.getId());
            return uploadedFile.getId();
        }
    }

    /**
     * Upload JSON data file
     */
    public String uploadJson(Path jsonFile) throws IOException {
        return uploadFile(jsonFile, "application/json");
    }

    /**
     * Upload PDF file
     */
    public String uploadPdf(Path pdfFile) throws IOException {
        return uploadFile(pdfFile, "application/pdf");
    }

    /**
     * Upload CSV file
     */
    public String uploadCsv(Path csvFile) throws IOException {
        return uploadFile(csvFile, "text/csv");
    }

    /**
     * Find existing file by name in the configured folder
     */
    private String findFileInFolder(String filename) {
        try {
            String query = String.format(
                    "name='%s' and '%s' in parents and trashed=false",
                    filename, folderId);

            FileList result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .execute();

            List<File> files = result.getFiles();
            return (files != null && !files.isEmpty()) ? files.get(0).getId() : null;
        } catch (Exception e) {
            log.warn("Could not check existing file {}: {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Create a subfolder inside the main folder
     */
    public String createSubfolder(String folderName) throws IOException {
        // Check if already exists
        String existingId = findFileInFolder(folderName);
        if (existingId != null) return existingId;

        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(folderId));

        File folder = driveService.files()
                .create(fileMetadata)
                .setFields("id")
                .execute();

        log.info("📁 Created Drive subfolder: {}", folderName);
        return folder.getId();
    }
}
