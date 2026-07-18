package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.entity.RecordNote;
import com.portal.service.RecordNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the per-instance archive zip:
 * history.csv + notes.html + attachments/ + manifest.json.
 * Includes every note anchored to the instance (TABLE streams and legacy
 * RECORD rows alike); soft-deleted rows are excluded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordNoteArchiveComponent {

    private final RecordNoteService recordNoteService;
    private final ProcessComponent processComponent;
    private final ObjectMapper objectMapper;

    public record ArchiveResult(String fileName, byte[] content) {
    }

    public ArchiveResult buildArchive(ProcessInstanceInfo detail) {
        String instanceId = detail.getId();
        List<RecordNote> notes = recordNoteService.findRecordNotesForArchive(instanceId);
        List<Map<String, Object>> history = processComponent.getProcessHistory(instanceId);

        // Attachments keep upload order; entry names get a numeric prefix against collisions.
        List<RecordNote> attachments = notes.stream()
                .filter(n -> RecordNote.TYPE_ATTACHMENT.equals(n.getNoteType()))
                .toList();
        Map<String, String> entryNameByNoteId = new LinkedHashMap<>();
        int seq = 1;
        for (RecordNote att : attachments) {
            entryNameByNoteId.put(att.getId(),
                    String.format("%03d_%s", seq++, zipSafeName(att.getFileName())));
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            putEntry(zip, "history.csv", historyCsv(history));
            putEntry(zip, "notes.html", notesHtml(detail, notes, entryNameByNoteId));
            for (RecordNote att : attachments) {
                zip.putNextEntry(new ZipEntry("attachments/" + entryNameByNoteId.get(att.getId())));
                zip.write(att.getFileContent() != null ? att.getFileContent() : new byte[0]);
                zip.closeEntry();
            }
            putEntry(zip, "manifest.json", manifestJson(attachments, entryNameByNoteId));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build archive for " + instanceId, e);
        }

        String base = detail.getBusinessKey() != null && !detail.getBusinessKey().isBlank()
                ? detail.getBusinessKey()
                : instanceId;
        return new ArchiveResult(zipSafeName(base) + "_archive.zip", buffer.toByteArray());
    }

    private String historyCsv(List<Map<String, Object>> history) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : history) {
            columns.addAll(row.keySet());
        }
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", columns.stream().map(RecordNoteArchiveComponent::csvCell).toList()));
        csv.append("\r\n");
        for (Map<String, Object> row : history) {
            List<String> cells = new ArrayList<>();
            for (String col : columns) {
                Object value = row.get(col);
                cells.add(csvCell(value == null ? "" : String.valueOf(value)));
            }
            csv.append(String.join(",", cells)).append("\r\n");
        }
        return csv.toString();
    }

    private String notesHtml(ProcessInstanceInfo detail, List<RecordNote> notes,
                             Map<String, String> entryNameByNoteId) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                .append("<title>").append(escape(title(detail))).append(" - Notes</title>")
                .append("<style>body{font-family:sans-serif;max-width:860px;margin:2rem auto;padding:0 1rem}")
                .append(".note{border:1px solid #ddd;border-radius:8px;padding:1rem;margin-bottom:1rem}")
                .append(".meta{color:#666;font-size:0.85rem;margin-bottom:0.5rem}")
                .append(".files{margin-top:0.5rem;font-size:0.9rem}")
                .append("img{max-width:100%}</style></head><body>")
                .append("<h1>").append(escape(title(detail))).append("</h1>");

        List<RecordNote> comments = notes.stream()
                .filter(n -> RecordNote.TYPE_COMMENT.equals(n.getNoteType()))
                .toList();
        List<RecordNote> standalone = notes.stream()
                .filter(n -> RecordNote.TYPE_ATTACHMENT.equals(n.getNoteType()) && n.getParentNoteId() == null)
                .toList();

        for (RecordNote comment : comments) {
            html.append("<div class=\"note\"><div class=\"meta\">")
                    .append(escape(author(comment))).append(" · ").append(comment.getCreatedAt())
                    .append("</div>");
            if (comment.getSubject() != null) {
                html.append("<h3>").append(escape(comment.getSubject())).append("</h3>");
            }
            html.append(rewriteInlineImages(comment.getBodyHtml(), entryNameByNoteId));
            List<RecordNote> children = notes.stream()
                    .filter(n -> comment.getId().equals(n.getParentNoteId())
                            && !Boolean.TRUE.equals(n.getIsInlineImage()))
                    .toList();
            if (!children.isEmpty()) {
                html.append("<div class=\"files\">");
                for (RecordNote child : children) {
                    html.append("<div><a href=\"attachments/")
                            .append(escape(entryNameByNoteId.get(child.getId()))).append("\">")
                            .append(escape(child.getFileName())).append("</a></div>");
                }
                html.append("</div>");
            }
            html.append("</div>");
        }
        if (!standalone.isEmpty()) {
            html.append("<div class=\"note\"><div class=\"meta\">Attachments</div><div class=\"files\">");
            for (RecordNote att : standalone) {
                html.append("<div><a href=\"attachments/")
                        .append(escape(entryNameByNoteId.get(att.getId()))).append("\">")
                        .append(escape(att.getFileName())).append("</a> · ")
                        .append(escape(author(att))).append(" · ").append(att.getCreatedAt())
                        .append("</div>");
            }
            html.append("</div></div>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    /** Rewrites same-origin inline-image URLs to relative attachments/ entries for offline viewing. */
    private String rewriteInlineImages(String bodyHtml, Map<String, String> entryNameByNoteId) {
        if (bodyHtml == null) {
            return "";
        }
        String result = bodyHtml;
        for (Map.Entry<String, String> e : entryNameByNoteId.entrySet()) {
            result = result.replace("/api/portal/record-notes/" + e.getKey() + "/content",
                    "attachments/" + e.getValue());
        }
        return result;
    }

    private String manifestJson(List<RecordNote> attachments, Map<String, String> entryNameByNoteId) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (RecordNote att : attachments) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", att.getId());
            entry.put("zipEntry", "attachments/" + entryNameByNoteId.get(att.getId()));
            entry.put("fileName", att.getFileName());
            entry.put("mimeType", att.getMimeType());
            entry.put("fileSize", att.getFileSize());
            entry.put("isInlineImage", att.getIsInlineImage());
            entry.put("parentNoteId", att.getParentNoteId());
            entry.put("uploadedBy", att.getCreatedBy());
            entry.put("uploadedByName", att.getCreatedByName());
            entry.put("uploadedAt", String.valueOf(att.getCreatedAt()));
            entries.add(entry);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String title(ProcessInstanceInfo detail) {
        if (detail.getTitle() != null && !detail.getTitle().isBlank()) {
            return detail.getTitle();
        }
        return detail.getProcessDefinitionName() != null ? detail.getProcessDefinitionName() : detail.getId();
    }

    private static String author(RecordNote note) {
        return note.getCreatedByName() != null && !note.getCreatedByName().isBlank()
                ? note.getCreatedByName()
                : note.getCreatedBy();
    }

    private static String zipSafeName(String name) {
        String safe = (name == null || name.isBlank() ? "file" : name)
                .replace("\\", "/");
        safe = safe.substring(safe.lastIndexOf('/') + 1);
        safe = safe.replaceAll("[\\x00-\\x1f\"*:<>?|]", "_").trim();
        return safe.isBlank() ? "file" : safe;
    }

    private static String csvCell(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
