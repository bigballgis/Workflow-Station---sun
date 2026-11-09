package com.admin.dto.response;

import java.util.List;

/**
 * Where a business unit or business role is used, shown before it is changed or deleted (platform access model §8.3):
 * a notice only, nothing is refused because of it.
 *
 * @param functionUnits function units whose access configuration, published processes or published views name it
 * @param others        other references, one line per kind (e.g. "BI dashboard assignments: 2")
 */
public record ReferenceNotice(List<FunctionUnit> functionUnits, List<String> others) {

    /** A function unit by code and current name. */
    public record FunctionUnit(String code, String name) {
    }
}
