package com.caisj.stockdashboard.backend.repository.file;

import com.caisj.stockdashboard.backend.config.AppProperties;
import com.caisj.stockdashboard.backend.domain.model.PortfolioItemRecord;
import com.caisj.stockdashboard.backend.repository.PortfolioRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JsonPortfolioRepository implements PortfolioRepository {

    private static final DateTimeFormatter BACKUP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public JsonPortfolioRepository(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PortfolioItemRecord> findAll() {
        Path path = portfolioFile();
        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(Files.readString(path), new TypeReference<>() { });
        } catch (IOException ex) {
            return List.of();
        }
    }

    @Override
    public List<PortfolioItemRecord> saveAll(List<PortfolioItemRecord> items) {
        Path file = portfolioFile();
        Path backupDir = backupDir();
        Path tempFile = Path.of(file + ".tmp");

        try {
            Files.createDirectories(file.getParent());
            Files.createDirectories(backupDir);
            if (Files.exists(file)) {
                backupExisting(file, backupDir);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), items);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            pruneBackups(backupDir);
            return items;
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Failed to save portfolio: " + ex.getMessage(), ex);
        }
    }

    private void backupExisting(Path file, Path backupDir) throws IOException {
        String fileName = "portfolio_" + LocalDateTime.now().format(BACKUP_FORMATTER) + ".json";
        Files.copy(file, backupDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
    }

    private void pruneBackups(Path backupDir) throws IOException {
        int limit = appProperties.getPortfolio().getBackupLimit();
        try (var stream = Files.list(backupDir)) {
            List<Path> backups = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith("portfolio_"))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparingLong(this::lastModified).reversed())
                .toList();

            for (int i = limit; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private Path portfolioFile() {
        return Path.of(appProperties.getPortfolio().getFile()).toAbsolutePath().normalize();
    }

    private Path backupDir() {
        return Path.of(appProperties.getPortfolio().getBackupDir()).toAbsolutePath().normalize();
    }
}
