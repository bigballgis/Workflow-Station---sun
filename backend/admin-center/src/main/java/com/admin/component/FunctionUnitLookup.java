package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared function unit lookups with the canonical not-found exceptions,
 * used by the function unit facade and its collaborator components.
 */
@Component
@RequiredArgsConstructor
public class FunctionUnitLookup {

    private final FunctionUnitRepository functionUnitRepository;
    private final I18nService i18nService;

    /**
     * Get function unit by id
     */
    public FunctionUnit getById(String id) {
        return functionUnitRepository.findById(id)
                .orElseThrow(() -> new FunctionUnitNotFoundException(
                        i18nService.getMessage("admin.fu.not_found_by_id", id)));
    }

    /**
     * Get function unit by code and version
     */
    public FunctionUnit getByCodeAndVersion(String code, String version) {
        return functionUnitRepository.findByCodeAndVersion(code, version)
                .orElseThrow(() -> new FunctionUnitNotFoundException(
                        i18nService.getMessage("admin.fu.not_found_by_code", code, version)));
    }
}
