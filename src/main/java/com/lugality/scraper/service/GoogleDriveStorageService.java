package com.lugality.scraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Storage service that saves locally AND uploads to Google Drive.
 * Activated when STORAGE_MODE=google_drive
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "scraper.storage-mode", havingValue = "google_drive")
public class GoogleDriveStorageService {

    private final LocalStorageService localStorageService;
    private final GoogleDriveService googleDriveService;

    @Autowired
    public GoogleDriveStorageService(
            LocalStorageService localStorageService,
            GoogleDriveService googleDriveService) {
        this.localStorageService = localStorageService;
        this.googleDriveService = googleDriveService;
    }

    /**
     * Save application data locally + upload JSON to Drive
     */
    public Path saveApplicationData(String appNumber, Map<String, Object> data) throws IOException {
        // Save locally first
        Path savedPath = localStorageService.saveApplicationData(appNumber, data);

        // Upload to Google Drive
        try {
            googleDriveService.uploadJson(savedPath);
        } catch (Exception e) {
            log.warn("⚠️ Drive upload failed for {}: {}", appNumber, e.getMessage());
        }

        return savedPath;
    }

    /**
     * Save documents metadata locally + upload to Drive
     */
    public void saveDocuments(String appNumber, List<Map<String, Object>> documents) throws IOException {
        localStorageService.saveDocuments(appNumber, documents);
    }

    /**
     * Save checkpoint locally + upload to Drive
     */
    public Path saveCheckpoint(
            List<String> queue,
            List<String> processed,
            List<Map<String, Object>> failed,
            String checkpointFile) throws IOException {

        Path savedPath = localStorageService.saveCheckpoint(queue, processed, failed, checkpointFile);

        try {
            googleDriveService.uploadJson(savedPath);
        } catch (Exception e) {
            log.warn("⚠️ Drive checkpoint upload failed: {}", e.getMessage());
        }

        return savedPath;
    }

    /**
     * Export CSV and upload to Drive
     */
    public Path exportToCsvAndUpload() throws IOException {
        Path csvPath = localStorageService.exportToCsv();

        try {
            googleDriveService.uploadCsv(csvPath);
            log.info("✅ CSV exported and uploaded to Google Drive");
        } catch (Exception e) {
            log.warn("⚠️ Drive CSV upload failed: {}", e.getMessage());
        }

        return csvPath;
    }

    public Optional<Map<String, Object>> loadApplicationData(String appNumber) {
        return localStorageService.loadApplicationData(appNumber);
    }

    public Set<String> getProcessedApplications() {
        return localStorageService.getProcessedApplications();
    }

    public Map<String, Object> getProgressSummary() {
        return localStorageService.getProgressSummary();
    }

    public Optional<Map<String, Object>> loadCheckpoint(String checkpointFile) {
        return localStorageService.loadCheckpoint(checkpointFile);
    }

    public void saveProgressEntry(int newProcessed, int newFailed, int totalInput,
                                   double durationSeconds, int workers) throws IOException {
        localStorageService.saveProgressEntry(newProcessed, newFailed, totalInput, durationSeconds, workers);
    }

    public Path exportToCsv() throws IOException {
        return exportToCsvAndUpload();
    }
}
