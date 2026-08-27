package com.admin.component;

import com.admin.exception.AdminBusinessException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a Function Unit ZIP into memory with zip-bomb and zip-slip guards.
 */
final class ImportZipReader {

    static final int MAX_ENTRIES = 2_000;
    static final int MAX_ENTRY_BYTES = 10 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 40 * 1024 * 1024;
    /** Base64 of a 40 MiB ZIP is about 56 MiB. */
    static final int MAX_BASE64_CHARS = 56 * 1024 * 1024;

    private ImportZipReader() {
    }

    static void assertBase64Length(String base64Content) {
        if (base64Content != null && base64Content.length() > MAX_BASE64_CHARS) {
            throw new AdminBusinessException("FU_IMPORT_PACKAGE_TOO_LARGE",
                    "Function unit package exceeds the maximum import size");
        }
    }

    static Map<String, byte[]> readEntries(byte[] zipBytes) throws IOException {
        Map<String, byte[]> rawFiles = new LinkedHashMap<>();
        int total = 0;
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null || name.isBlank() || name.contains("..")
                        || name.startsWith("/") || name.startsWith("\\")) {
                    throw new AdminBusinessException("FU_IMPORT_INVALID_PACKAGE",
                            "Function unit package contains an illegal zip entry path");
                }
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new AdminBusinessException("FU_IMPORT_PACKAGE_TOO_LARGE",
                            "Function unit package has too many zip entries");
                }
                byte[] data = readEntry(zis);
                total += data.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new AdminBusinessException("FU_IMPORT_PACKAGE_TOO_LARGE",
                            "Function unit package uncompressed size exceeds the limit");
                }
                rawFiles.put(name, data);
            }
        }
        return rawFiles;
    }

    private static byte[] readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        int written = 0;
        while ((len = zis.read(buffer)) > 0) {
            written += len;
            if (written > MAX_ENTRY_BYTES) {
                throw new AdminBusinessException("FU_IMPORT_PACKAGE_TOO_LARGE",
                        "A file inside the function unit package exceeds the per-entry size limit");
            }
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
}
