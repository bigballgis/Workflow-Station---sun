package com.portal.util;

import com.portal.dto.RecordNoteDtos.AttachmentInfo;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.entity.RecordNote;

import java.util.ArrayList;
import java.util.List;

/**
 * One-line human-readable summaries of a Record Note, written into the
 * {@code old_value} / {@code new_value} columns of the instance change history.
 *
 * Shape: {@code subject · body text · file1, file2}. Only the parts a note actually
 * has are joined, so a standalone attachment renders as just its file name and a
 * plain comment as just its text.
 */
public final class RecordNoteAuditSummary {

    /** Keep audit values well inside the TEXT column and readable in the history table. */
    private static final int MAX_LENGTH = 1000;
    private static final String SEPARATOR = " · ";

    private RecordNoteAuditSummary() {
    }

    /**
     * Summary of a freshly created note. File names come from the request rather than the
     * returned item because an attachment-only create stores every file as its own row and
     * returns only the first one.
     */
    public static String created(NoteItem item, List<String> uploadedFileNames) {
        if (item == null) {
            return join(new ArrayList<>(), uploadedFileNames);
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, item.getSubject());
        addIfPresent(parts, item.getBodyText());
        List<String> files = new ArrayList<>(names(uploadedFileNames));
        if (files.isEmpty()) {
            addIfPresent(files, item.getFileName());
        }
        for (AttachmentInfo attachment : safe(item.getAttachments())) {
            if (attachment != null && !Boolean.TRUE.equals(attachment.getIsInlineImage())
                    && !files.contains(attachment.getFileName())) {
                addIfPresent(files, attachment.getFileName());
            }
        }
        return join(parts, files);
    }

    /** Summary of a stored note — used for the pre-edit snapshot and for deletions. */
    public static String existing(RecordNote note) {
        if (note == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, note.getSubject());
        addIfPresent(parts, note.getBodyText() != null
                ? note.getBodyText()
                : RecordNoteHtmlSupport.extractText(note.getBodyHtml()));
        List<String> files = new ArrayList<>();
        addIfPresent(files, note.getFileName());
        return join(parts, files);
    }

    /** Summary of a note after an edit (the detail response carries HTML only). */
    public static String updated(NoteDetail detail) {
        if (detail == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, detail.getSubject());
        addIfPresent(parts, RecordNoteHtmlSupport.extractText(detail.getBodyHtml()));
        List<String> files = new ArrayList<>();
        for (AttachmentInfo attachment : safe(detail.getAttachments())) {
            if (attachment != null && !Boolean.TRUE.equals(attachment.getIsInlineImage())) {
                addIfPresent(files, attachment.getFileName());
            }
        }
        return join(parts, files);
    }

    private static String join(List<String> parts, List<String> fileNames) {
        List<String> all = new ArrayList<>(parts);
        List<String> files = names(fileNames);
        if (!files.isEmpty()) {
            all.add(String.join(", ", files));
        }
        String value = String.join(SEPARATOR, all);
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }

    private static List<String> names(List<String> fileNames) {
        List<String> out = new ArrayList<>();
        for (String name : safe(fileNames)) {
            addIfPresent(out, name);
        }
        return out;
    }

    private static void addIfPresent(List<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
