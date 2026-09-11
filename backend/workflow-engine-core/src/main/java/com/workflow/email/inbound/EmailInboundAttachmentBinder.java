package com.workflow.email.inbound;

import com.workflow.client.DeveloperWorkstationFileClient;
import com.workflow.email.extract.EmailAttachment;
import com.workflow.email.extract.EmailExtractionSpec;
import com.workflow.email.extract.EmailExtractionSpec.FieldRule;
import com.workflow.email.extract.EmailExtractionSpec.Source;
import com.workflow.email.extract.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uploads inbound attachments and optional original RFC822 through DW {@code POST /upload}
 * and writes FILE field values for {@code ATTACHMENTS} / {@code RAW_EML} mapping rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailInboundAttachmentBinder {

    private static final String EML_CONTENT_TYPE = "message/rfc822";

    private final DeveloperWorkstationFileClient fileClient;

    public BindResult bind(EmailMessage email, EmailExtractionSpec spec) {
        List<FieldRule> attachmentRules = rulesFor(spec, Source.ATTACHMENTS);
        List<FieldRule> rawRules = rulesFor(spec, Source.RAW_EML);
        if (attachmentRules.isEmpty() && rawRules.isEmpty()) {
            return BindResult.empty();
        }
        List<String> errors = new ArrayList<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        List<String> names = new ArrayList<>();
        bindAttachments(email, attachmentRules, names, errors, fields, missing);
        bindRawEml(email, rawRules, errors, fields, missing);
        return new BindResult(fields, names, errors, missing);
    }

    private void bindAttachments(
            EmailMessage email,
            List<FieldRule> rules,
            List<String> names,
            List<String> errors,
            Map<String, Object> fields,
            List<String> missing) {
        if (rules.isEmpty()) {
            return;
        }
        names.addAll(email.attachmentNames());
        List<EmailFileFieldValue.StoredUpload> stored = uploadEligible(email.attachments(), errors);
        applyFileValue(rules, EmailFileFieldValue.persist(stored, EmailFileFieldValue.DEFAULT_MAX_FILES),
                fields, missing);
    }

    private void bindRawEml(
            EmailMessage email,
            List<FieldRule> rules,
            List<String> errors,
            Map<String, Object> fields,
            List<String> missing) {
        if (rules.isEmpty()) {
            return;
        }
        applyFileValue(rules, uploadRawEml(email, errors), fields, missing);
    }

    private static void applyFileValue(
            List<FieldRule> rules,
            String fileValue,
            Map<String, Object> fields,
            List<String> missing) {
        for (FieldRule rule : rules) {
            if (StringUtils.hasText(fileValue)) {
                fields.put(rule.getTarget(), fileValue);
            } else if (rule.isRequired()) {
                missing.add(rule.getTarget());
            }
        }
    }

    private List<EmailFileFieldValue.StoredUpload> uploadEligible(
            List<EmailAttachment> attachments, List<String> errors) {
        List<EmailFileFieldValue.StoredUpload> stored = new ArrayList<>();
        if (attachments == null) {
            return stored;
        }
        for (EmailAttachment attachment : attachments) {
            Optional<EmailFileFieldValue.StoredUpload> uploaded = uploadOne(attachment, stored.size(), errors);
            uploaded.ifPresent(stored::add);
        }
        return stored;
    }

    private Optional<EmailFileFieldValue.StoredUpload> uploadOne(
            EmailAttachment attachment, int alreadyStored, List<String> errors) {
        String name = StringUtils.hasText(attachment.filename()) ? attachment.filename() : "attachment";
        return uploadBytes(name, attachment.contentType(), attachment.content(), alreadyStored, errors);
    }

    private String uploadRawEml(EmailMessage email, List<String> errors) {
        byte[] raw = email.rawRfc822();
        String name = emlFilename(email.subject());
        Optional<EmailFileFieldValue.StoredUpload> uploaded =
                uploadBytes(name, EML_CONTENT_TYPE, raw, 0, errors);
        if (uploaded.isEmpty()) {
            return "";
        }
        return EmailFileFieldValue.persist(List.of(uploaded.get()), 1);
    }

    private Optional<EmailFileFieldValue.StoredUpload> uploadBytes(
            String name, String contentType, byte[] content, int alreadyStored, List<String> errors) {
        if (content == null || content.length == 0) {
            errors.add(name + ": empty");
            return Optional.empty();
        }
        if (content.length > EmailFileFieldValue.MAX_FILE_SIZE_BYTES) {
            errors.add(name + ": exceeds 50MB");
            return Optional.empty();
        }
        if (alreadyStored >= EmailFileFieldValue.DEFAULT_MAX_FILES) {
            errors.add(name + ": maxFiles exceeded");
            return Optional.empty();
        }
        Optional<DeveloperWorkstationFileClient.UploadedFileRef> uploaded =
                fileClient.upload(name, contentType, content);
        if (uploaded.isEmpty()) {
            errors.add(name + ": upload failed");
            return Optional.empty();
        }
        return Optional.of(new EmailFileFieldValue.StoredUpload(uploaded.get().url(), uploaded.get().name()));
    }

    static String emlFilename(String subject) {
        String base = StringUtils.hasText(subject) ? subject.trim() : "message";
        String cleaned = base.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_");
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        if (!StringUtils.hasText(cleaned)) {
            cleaned = "message";
        }
        return cleaned + ".eml";
    }

    private static List<FieldRule> rulesFor(EmailExtractionSpec spec, Source source) {
        List<FieldRule> rules = new ArrayList<>();
        if (spec == null || spec.getFields() == null) {
            return rules;
        }
        for (FieldRule rule : spec.getFields()) {
            if (rule != null && rule.getSource() == source && StringUtils.hasText(rule.getTarget())) {
                rules.add(rule);
            }
        }
        return rules;
    }

    public record BindResult(
            Map<String, Object> fields,
            List<String> attachmentNames,
            List<String> attachmentErrors,
            List<String> missingRequired) {
        public static BindResult empty() {
            return new BindResult(Map.of(), List.of(), List.of(), List.of());
        }
    }
}
