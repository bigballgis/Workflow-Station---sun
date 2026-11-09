package com.admin.dto.request;

import java.util.List;

/**
 * Body of the engine's claim-pool resync call (platform access model §7.3, WP8b-2): the BU + Role pools whose
 * holders changed. Ids and codes both travel because deployed BPMN names a pool by code (current designer) or by
 * id (older deployments); the engine matches either and re-reads the holders itself.
 */
public record ClaimPoolResyncRequest(List<Pool> pools) {

    public record Pool(String businessUnitId, String businessUnitCode, String roleId, String roleCode) {
    }
}
