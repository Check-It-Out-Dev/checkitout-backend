package com.sm.instagram.platform.common.security.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service responsible ONLY for MaxMind database management.
 * This is shared by both Redis and in-memory GeoLocation implementations.
 * <p>
 * Responsibilities:
 * - Download database from MaxMind
 * - Update database periodically
 * - Provide database reader for lookups
 * - Extract tar.gz files
 * <p>
 * Single Responsibility: Database lifecycle management
 */
@Slf4j
@Service
public class MaxMindDatabaseService {

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private DatabaseReader databaseReader;
    private LocalDateTime lastUpdateTime;

    @Value("${maxmind.license.key:}")
    private String licenseKey;

    @Value("${maxmind.database.path:./geoip/GeoLite2-City.mmdb}")
    private String databasePath;

    @Value("${maxmind.update.enabled:true}")
    private boolean updateEnabled;

    @PostConstruct
    public void init() {
        log.info("Initializing MaxMind Database Service");

        try {
            // Create directory if it doesn't exist
            Path geoipDir = Paths.get("./geoip");
            if (!Files.exists(geoipDir)) {
                Files.createDirectories(geoipDir);
                log.info("Created GeoIP directory: {}", geoipDir.toAbsolutePath());
            }

            File database = new File(databasePath);
            if (database.exists()) {
                loadDatabase(database);
            } else {
                log.warn("GeoLite2 database not found at {}. Downloading...", databasePath);
                downloadDatabase();
            }

            log.info("MaxMind Database Service initialized successfully");
        } catch (Exception e) {
            // Degraded mode instead of context death: lookupCity() already
            // null-guards the reader (every lookup answers "unknown"), and a
            // cred-less/offline clone (no .mmdb, no license key, synthetic
            // Google credentials) must still boot — GeoIP is an enhancement,
            // not a boot dependency. The scheduled update keeps retrying.
            log.error("Failed to initialize MaxMind database — GeoIP runs in "
                    + "degraded mode (all lookups return unknown) until a "
                    + "database is available", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (databaseReader != null) {
            try {
                databaseReader.close();
            } catch (IOException e) {
                log.error("Failed to close MaxMind reader", e);
            }
        }
        executorService.shutdown();
    }

    /**
     * Load database from file.
     */
    private void loadDatabase(File database) throws IOException {
        databaseReader = new DatabaseReader.Builder(database)
                .withCache(new CHMCache(4096))  // Cache for 4096 lookups
                .build();
        lastUpdateTime = LocalDateTime.now();
        log.info("MaxMind database loaded from: {}", database.getAbsolutePath());
    }

    /**
     * Download database from MaxMind.
     */
    public void downloadDatabase() {
        if (licenseKey == null || licenseKey.isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "MaxMind license key");
        }

        try {
            String url = String.format(
                    "https://download.maxmind.com/app/geoip_download?" +
                            "edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz",
                    licenseKey
            );

            Path tempFile = Files.createTempFile("GeoLite2-City", ".tar.gz");
            downloadFile(url, tempFile);

            Path extractedDb = extractDatabase(tempFile);

            Path targetPath = Paths.get(databasePath);
            Files.move(extractedDb, targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            loadDatabase(new File(databasePath));

            Files.deleteIfExists(tempFile);

            log.info("GeoLite2 database downloaded and loaded successfully");
        } catch (Exception e) {
            log.error("Failed to download from MaxMind", e);
            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Lookup IP address in MaxMind database.
     * Returns null if not found or error occurs.
     */
    public CityResponse lookupCity(String ip) {
        if (databaseReader == null) {
            log.warn("MaxMind reader not initialized");
            return null;
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            return databaseReader.city(ipAddress);
        } catch (IOException | GeoIp2Exception e) {
            log.debug("MaxMind lookup failed for IP {}: {}", maskIp(ip), e.getMessage());
            return null;
        }
    }

    /**
     * Async lookup with executor service.
     */
    public CompletableFuture<CityResponse> lookupCityAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> lookupCity(ip), executorService);
    }

    /**
     * Check if database is loaded and ready.
     */
    public boolean isDatabaseReady() {
        return databaseReader != null;
    }

    /**
     * Get database file information.
     */
    public DatabaseInfo getDatabaseInfo() {
        File database = new File(databasePath);
        return DatabaseInfo.builder()
                .exists(database.exists())
                .path(database.getAbsolutePath())
                .sizeBytes(database.length())
                .lastModified(database.lastModified())
                .lastUpdateTime(lastUpdateTime)
                .ready(isDatabaseReady())
                .build();
    }

    /**
     * Update database from MaxMind.
     * Scheduled to run weekly on Wednesday at 2 AM.
     */
    @Scheduled(cron = "${maxmind.update.cron:0 0 2 ? * WED}")
    public void updateDatabase() {
        if (!updateEnabled || licenseKey == null || licenseKey.isEmpty()) {
            log.debug("Database update disabled or no license key");
            return;
        }

        try {
            log.info("Starting scheduled database update");
            downloadDatabase();
            log.info("Database update completed successfully");
        } catch (Exception e) {
            log.error("Database update failed", e);
        }
    }

    /**
     * Download file from URL.
     */
    private void downloadFile(String url, Path destination) throws IOException {
        try (InputStream in = new URI(url).toURL().openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(destination.toFile())) {

            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Extract database from tar.gz file.
     */
    private Path extractDatabase(Path tarGzFile) throws IOException {
        Path tempDir = Files.createTempDirectory("geolite2");

        try (InputStream fi = Files.newInputStream(tarGzFile);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
                if (entry.getName().endsWith("GeoLite2-City.mmdb")) {
                    Path extractedFile = tempDir.resolve("GeoLite2-City.mmdb");
                    Files.copy(tar, extractedFile, StandardCopyOption.REPLACE_EXISTING);
                    return extractedFile;
                }
            }
        }

        throw new ResourceNotFoundException("error.business.item_not_found", "Database file in archive");
    }

    /**
     * GDPR compliant IP masking.
     */
    private String maskIp(String ip) {
        if (ip == null) return "unknown";

        if (ip.contains(".")) {
            // IPv4: mask last octet
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot) + ".xxx" : "masked";
        } else if (ip.contains(":")) {
            // IPv6: mask last 4 segments
            int lastColon = ip.lastIndexOf(':');
            return lastColon > 0 ? ip.substring(0, lastColon) + ":xxxx" : "masked";
        }
        return "masked";
    }

    /**
     * Database information DTO.
     */
    @lombok.Builder
    @lombok.Data
    public static class DatabaseInfo {
        private boolean exists;
        private String path;
        private long sizeBytes;
        private long lastModified;
        private LocalDateTime lastUpdateTime;
        private boolean ready;
    }
}
