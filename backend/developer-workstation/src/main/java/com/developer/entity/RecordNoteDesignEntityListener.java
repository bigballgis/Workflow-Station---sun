package com.developer.entity;

import com.platform.common.recordnote.RecordNoteDesign;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/** All persistence paths, including Import/Clone/Rollback, preserve the same control contract. */
public class RecordNoteDesignEntityListener {
    @PrePersist @PreUpdate
    public void prepare(FormDefinition form) {
        RecordNoteDesign.prepare(form.getConfigJson(),false);
    }
}
