package com.developer.component.impl;

import com.developer.dto.FormTableBindingRequest;
import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.FormTableBindingRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Same-table duplicate policy for Manage Table Bindings.
 *
 * <p>SUB bindings may share a table when each uses a different declared filter FK.
 * PRIMARY / RELATED / other types stay one binding per table (or relation table).
 */
@Component
@RequiredArgsConstructor
public class FormTableBindingUniquenessGuard {

    private final FormTableBindingRepository formTableBindingRepository;
    private final I18nService i18nService;

    public void assertCreate(long formId, FormTableBindingRequest request,
                      boolean isRelationTable, Long resolvedFilterFkFieldId) {
        if (isRelationTable) {
            if (formTableBindingRepository.existsByFormIdAndRelationTableId(
                    formId, request.getRelationTableId())) {
                throw duplicate();
            }
            return;
        }
        if (request.getTableId() == null) {
            return;
        }
        if (request.getBindingType() == BindingType.SUB) {
            rejectDuplicateSubFilter(formId, request.getTableId(), resolvedFilterFkFieldId, null);
            return;
        }
        if (formTableBindingRepository.existsByFormIdAndTableId(formId, request.getTableId())) {
            throw duplicate();
        }
    }

    public void assertUpdate(FormTableBinding existing, FormTableBindingRequest request,
                      Long resolvedFilterFkFieldId) {
        if (request.getBindingType() != BindingType.SUB) {
            return;
        }
        Long tableId = existing.getTableId();
        if (tableId == null) {
            return;
        }
        rejectDuplicateSubFilter(existing.getFormId(), tableId, resolvedFilterFkFieldId, existing.getId());
    }

    private void rejectDuplicateSubFilter(long formId, long tableId, Long filterFkFieldId,
                                          Long excludeBindingId) {
        for (FormTableBinding sibling : formTableBindingRepository.findByFormIdAndTableId(formId, tableId)) {
            if (excludeBindingId != null && excludeBindingId.equals(sibling.getId())) {
                continue;
            }
            if (sibling.getBindingType() != BindingType.SUB) {
                throw duplicate();
            }
            if (Objects.equals(sibling.getFilterFkFieldId(), filterFkFieldId)) {
                throw duplicate();
            }
        }
    }

    private DeveloperBusinessException duplicate() {
        return new DeveloperBusinessException(
                "BINDING_EXISTS",
                i18nService.getMessage("form.binding_exists"),
                i18nService.getMessage("form.no_duplicate_binding"));
    }
}
