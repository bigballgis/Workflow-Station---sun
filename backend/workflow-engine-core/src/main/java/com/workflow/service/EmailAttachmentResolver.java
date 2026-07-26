package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.workflow.client.DeveloperWorkstationFileClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.BpmnError;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves Send Email {@code emailAttachments} field refs into MIME parts.
 * Supports MAIN FILE fields, SUB-table FILE fields ({@code __subTables__}),
 * and Lookup-target FILE fields (embedded RT row objects).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAttachmentResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeveloperWorkstationFileClient fileClient;
    private final I18nService i18nService;

    public List<EmailSendOptions.EmailAttachmentPart> resolve(String rawJson, Map<String, Object> variables) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        List<Map<String, Object>> items;
        try {
            items = OBJECT_MAPPER.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse emailAttachments JSON: {}", e.getMessage());
            return List.of();
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<EmailSendOptions.EmailAttachmentPart> parts = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            collectUrls(item, variables).forEach(url -> appendDownloaded(parts, url));
        }
        return parts;
    }

    private List<String> collectUrls(Map<String, Object> item, Map<String, Object> variables) {
        String source = item.get("source") != null ? item.get("source").toString().trim() : "";
        if ("main".equalsIgnoreCase(source)) {
            String fieldName = stringVal(item.get("fieldName"));
            if (!StringUtils.hasText(fieldName)) {
                return List.of();
            }
            return extractUploadUrls(variables.get(fieldName));
        }
        if ("sub".equalsIgnoreCase(source)) {
            String bindingId = stringVal(item.get("bindingId"));
            String fieldName = stringVal(item.get("fieldName"));
            if (!StringUtils.hasText(bindingId) || !StringUtils.hasText(fieldName)) {
                return List.of();
            }
            return extractSubTableFieldUrls(variables, bindingId, fieldName);
        }
        if ("lookup".equalsIgnoreCase(source)) {
            String lookupField = stringVal(item.get("lookupField"));
            String targetField = stringVal(item.get("targetField"));
            if (!StringUtils.hasText(lookupField) || !StringUtils.hasText(targetField)) {
                return List.of();
            }
            return extractLookupTargetUrls(variables.get(lookupField), targetField);
        }
        // Legacy {name,content} free-form is no longer supported.
        log.warn("Ignoring legacy or invalid emailAttachments item (source required)");
        return List.of();
    }

    private List<String> extractSubTableFieldUrls(
            Map<String, Object> variables, String bindingId, String fieldName) {
        Object subTablesObj = variables != null ? variables.get("__subTables__") : null;
        if (!(subTablesObj instanceof Map<?, ?> subTables)) {
            return List.of();
        }
        Object rowsObj = subTables.get(bindingId);
        if (rowsObj == null) {
            // JSON object keys are strings; tolerate numeric key materialization.
            try {
                rowsObj = subTables.get(Long.parseLong(bindingId));
            } catch (NumberFormatException ignored) {
                rowsObj = null;
            }
        }
        if (!(rowsObj instanceof Collection<?> rows)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            urls.addAll(extractUploadUrls(map.get(fieldName)));
        }
        return urls;
    }

    private List<String> extractLookupTargetUrls(Object lookupValue, String targetField) {
        List<String> urls = new ArrayList<>();
        for (Object row : asRowList(lookupValue)) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            urls.addAll(extractUploadUrls(map.get(targetField)));
        }
        return urls;
    }

    private static List<Object> asRowList(Object lookupValue) {
        if (lookupValue == null) {
            return List.of();
        }
        if (lookupValue instanceof Collection<?> col) {
            return new ArrayList<>(col);
        }
        return List.of(lookupValue);
    }

    private List<String> extractUploadUrls(Object value) {
        List<String> urls = new ArrayList<>();
        collectUploadUrls(value, urls);
        return urls;
    }

    private void collectUploadUrls(Object value, List<String> out) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.contains("/upload/files/")) {
                out.add(trimmed);
            }
            return;
        }
        if (value instanceof Collection<?> col) {
            for (Object item : col) {
                collectUploadUrls(item, out);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object url = firstNonNull(map.get("url"), map.get("fileUrl"), map.get("file_url"));
            if (url instanceof String s && s.trim().contains("/upload/files/")) {
                out.add(s.trim());
                return;
            }
            Object nested = map.get("response");
            if (nested instanceof Map<?, ?> resp) {
                Object data = resp.get("data");
                collectUploadUrls(data != null ? data : nested, out);
            }
        }
    }

    private void appendDownloaded(List<EmailSendOptions.EmailAttachmentPart> parts, String url) {
        Optional<DeveloperWorkstationFileClient.DownloadedFile> file = fileClient.downloadByStoredUrl(url);
        if (file.isEmpty()) {
            log.error("Configured email attachment download failed");
            throw new BpmnError("EMAIL_ATTACHMENT_FAILED",
                    i18nService.getMessage("email.send_task.attachment_download_failed"));
        }
        DeveloperWorkstationFileClient.DownloadedFile downloaded = file.get();
        if (downloaded.content() == null || downloaded.content().length == 0) {
            log.error("Configured email attachment download returned empty content");
            throw new BpmnError("EMAIL_ATTACHMENT_FAILED",
                    i18nService.getMessage("email.send_task.attachment_download_failed"));
        }
        String name = StringUtils.hasText(downloaded.fileName()) ? downloaded.fileName() : "attachment";
        String content = Base64.getEncoder().encodeToString(downloaded.content());
        parts.add(new EmailSendOptions.EmailAttachmentPart(name, content));
    }

    private static String stringVal(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }

    private static Object firstNonNull(Object a, Object b, Object c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }
}
