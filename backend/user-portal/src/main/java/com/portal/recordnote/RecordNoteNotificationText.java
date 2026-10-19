package com.portal.recordnote;

import com.portal.util.RecordNoteHtmlSupport;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Map;

/** Plain-text notification copy. Source is the immutable, server-verified submission snapshot. */
final class RecordNoteNotificationText {
    private RecordNoteNotificationText() {}

    static String content(RecordNoteCatalogRepository.Request request, Map<String, Object> submission,
                          Map<String, Object> policy, Locale locale, MessageSource messages) {
        String name = RecordNoteHtmlSupport.extractText(request.title());
        // FALLBACK(ux): an unnamed Request remains identifiable by its complete ID.
        if (name == null) name = messages.getMessage("record_note.request", null, locale);
        String location = messages.getMessage("TABLE".equals(submission.get("target_type"))
                ? "record_note.case_notes" : "record_note.transaction_notes", null, locale);
        if ("RECORD".equals(submission.get("target_type"))) location += " · " + submission.get("target_id");
        String content = messages.getMessage("record_note.notification_details",
                new Object[]{name, request.id(), submission.get("attachment_count"), location}, locale);
        // Old submissions never recorded a trustworthy upload source. Do not infer it from recipients.
        if (!policy.containsKey("uploadSource")) return content;
        if (!(policy.get("uploadSource") instanceof Map<?, ?> source)) throw new IllegalStateException("NOTE_SOURCE_INVALID");
        String label = switch (String.valueOf(source.get("kind"))) {
            case "TASK" -> {
                String taskName = source.get("taskName") instanceof String text ? text : null;
                // FALLBACK(ux): an unnamed task is still a verified source, identified by its task ID.
                if (taskName == null || taskName.isBlank()) {
                    yield messages.getMessage("record_note.source_task", null, locale) + " (" + source.get("taskId") + ")";
                }
                yield taskName;
            }
            case "MY_REQUEST" -> messages.getMessage("record_note.source_my_request", null, locale);
            case "AUDIT" -> messages.getMessage("record_note.source_audit", null, locale);
            case "NEW_REQUEST" -> messages.getMessage("record_note.source_new_request", null, locale);
            default -> throw new IllegalStateException("NOTE_SOURCE_INVALID");
        };
        return content + "\n" + messages.getMessage("record_note.uploaded_from", new Object[]{label}, locale);
    }
}
