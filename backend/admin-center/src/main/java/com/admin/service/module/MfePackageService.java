package com.admin.service.module;

import com.admin.config.MfeStoreConfig;
import com.admin.dto.module.ImportPackageResult;
import com.admin.dto.module.MfeManifest;
import com.admin.entity.module.FrontendModuleRegistry;
import com.admin.repository.module.FrontendModuleRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfePackageService {

    private final MfeStoreConfig storeConfig;
    private final FrontendModuleRegistryRepository registryRepo;
    private final ObjectMapper objectMapper;

    // ==================== Export ====================

    public Path exportPackage(String tenantId, Long registryId) throws IOException {
        FrontendModuleRegistry module = registryRepo.findByIdAndTenantId(registryId, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + registryId));

        Path moduleDir = storeConfig.resolveModulePath(
                module.getModuleCode(), module.getVersion());

        if (!Files.exists(moduleDir) || !Files.isDirectory(moduleDir)) {
            throw new RuntimeException(
                    "MFE_PACKAGE_NOT_FOUND: no package stored for " +
                    module.getModuleCode() + " v" + module.getVersion());
        }

        Path tempZip = Files.createTempFile("mfe-export-", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
            Files.walkFileTree(moduleDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    String entryName = moduleDir.relativize(file).toString();
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    String entryName = moduleDir.relativize(dir).toString();
                    if (!entryName.isEmpty()) {
                        zos.putNextEntry(new ZipEntry(entryName + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.info("Exported package for {} v{} -> {}",
                module.getModuleCode(), module.getVersion(), tempZip);
        return tempZip;
    }

    // ==================== Import ====================

    @Transactional
    public ImportPackageResult importPackage(String tenantId, String targetEnv,
                                              InputStream zipStream) {
        Path tempDir = null;
        try {
            // 1. Extract zip to temp dir
            tempDir = Files.createTempDirectory("mfe-import-");
            try (ZipInputStream zis = new ZipInputStream(zipStream)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path dest = tempDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }

            // 2. Read manifest.json
            Path manifestPath = tempDir.resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                throw new RuntimeException(
                        "MFE_IMPORT_NO_MANIFEST: zip must contain manifest.json at root");
            }
            MfeManifest manifest = objectMapper.readValue(
                    manifestPath.toFile(), MfeManifest.class);

            // 3. Validate required fields
            if (manifest.getModuleCode() == null || manifest.getModuleCode().isBlank()) {
                throw new RuntimeException(
                        "MFE_IMPORT_INVALID_MANIFEST: moduleCode is required");
            }
            if (manifest.getVersion() == null || manifest.getVersion().isBlank()) {
                throw new RuntimeException(
                        "MFE_IMPORT_INVALID_MANIFEST: version is required");
            }
            if (manifest.getHostApp() == null || manifest.getHostApp().isBlank()) {
                throw new RuntimeException(
                        "MFE_IMPORT_INVALID_MANIFEST: hostApp is required");
            }

            // 4. Validate dist/ exists
            Path distDir = tempDir.resolve("dist");
            Path remoteEntry = distDir.resolve("assets").resolve("remoteEntry.js");
            if (!Files.exists(remoteEntry)) {
                throw new RuntimeException(
                        "MFE_IMPORT_NO_REMOTE_ENTRY: dist/assets/remoteEntry.js not found");
            }

            // 5. Check if module already registered in target env
            Optional<FrontendModuleRegistry> existing = registryRepo
                    .findByTenantIdAndHostAppAndEnvAndModuleCode(
                            tenantId, manifest.getHostApp(), targetEnv,
                            manifest.getModuleCode());

            String remoteEntryUrl = "/mfe-store/" + manifest.getModuleCode()
                    + "/" + manifest.getVersion() + "/dist/assets/remoteEntry.js";

            // 6. Copy files to store
            Path storeDir = storeConfig.resolveModulePath(
                    manifest.getModuleCode(), manifest.getVersion());
            if (Files.exists(storeDir)) {
                deleteRecursively(storeDir);
            }
            Files.createDirectories(storeDir);
            copyDir(tempDir, storeDir);

            // 7. Register or update in DB
            FrontendModuleRegistry registry;
            if (existing.isPresent()) {
                registry = existing.get();
                registry.setVersion(manifest.getVersion());
                registry.setRemoteEntryUrl(remoteEntryUrl);
                if (manifest.getDisplayName() != null) {
                    registry.setDisplayName(manifest.getDisplayName());
                }
                if (manifest.getRoutePath() != null) {
                    registry.setRoutePath(manifest.getRoutePath());
                }
                if (manifest.getIcon() != null) {
                    registry.setIcon(manifest.getIcon());
                }
                registry.setOrderNo(manifest.getOrderNo());
                if (manifest.getExposedModule() != null) {
                    registry.setExposedModule(manifest.getExposedModule());
                }
                registry.setRequiredPermissions(manifest.getRequiredPermissions());
                registry.setTenantScope(manifest.getTenantScope());
                registry.setEnabled(true);
            } else {
                registry = FrontendModuleRegistry.builder()
                        .tenantId(tenantId)
                        .hostApp(manifest.getHostApp())
                        .moduleCode(manifest.getModuleCode())
                        .displayName(manifest.getDisplayName() != null
                                ? manifest.getDisplayName() : manifest.getModuleCode())
                        .routePath(manifest.getRoutePath() != null
                                ? manifest.getRoutePath()
                                : "/mfe/" + manifest.getModuleCode())
                        .icon(manifest.getIcon() != null ? manifest.getIcon() : "")
                        .orderNo(manifest.getOrderNo() > 0 ? manifest.getOrderNo() : 100)
                        .remoteEntryUrl(remoteEntryUrl)
                        .exposedModule(manifest.getExposedModule() != null
                                ? manifest.getExposedModule() : "./App")
                        .enabled(true)
                        .requiredPermissions(manifest.getRequiredPermissions())
                        .tenantScope(manifest.getTenantScope())
                        .env(targetEnv)
                        .version(manifest.getVersion())
                        .build();
            }
            registry = registryRepo.save(registry);

            log.info("Imported package {} v{} -> registry id={}, env={}",
                    manifest.getModuleCode(), manifest.getVersion(),
                    registry.getId(), targetEnv);

            return ImportPackageResult.builder()
                    .success(true)
                    .moduleCode(manifest.getModuleCode())
                    .version(manifest.getVersion())
                    .registryId(registry.getId())
                    .remoteEntryUrl(remoteEntryUrl)
                    .build();

        } catch (RuntimeException e) {
            log.error("Import failed: {}", e.getMessage());
            return ImportPackageResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Import failed with unexpected error", e);
            return ImportPackageResult.builder()
                    .success(false)
                    .error("MFE_IMPORT_FAILED: " + e.getMessage())
                    .build();
        } finally {
            if (tempDir != null) {
                try { deleteRecursively(tempDir); } catch (IOException ignored) {}
            }
        }
    }

    // ==================== Helpers ====================

    private static void copyDir(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
