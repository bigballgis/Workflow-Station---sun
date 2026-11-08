package com.portal.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One automation write key replays the first body and releases the claim when the work fails. */
@Component
public class AutomationIdempotency {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AutomationIdempotency(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> once(String idempotencyKey, Map<String, Object> body,
                                    Supplier<Map<String, Object>> action) {
        AutomationAccessRules.rejectActor(body);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "Idempotency-Key is required");
        }
        String hash = hash(body);
        if (!claim(idempotencyKey, hash)) {
            return replay(load(idempotencyKey), hash);
        }
        Map<String, Object> result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            jdbc.update("DELETE FROM up_automation_write_ledger WHERE idempotency_key = ? AND response_body = ''",
                    idempotencyKey);
            throw ex;
        }
        jdbc.update("UPDATE up_automation_write_ledger SET response_body = ? WHERE idempotency_key = ?",
                json(result), idempotencyKey);
        return result;
    }

    private boolean claim(String idempotencyKey, String hash) {
        try {
            jdbc.update("""
                    INSERT INTO up_automation_write_ledger (idempotency_key, request_hash, response_body)
                    VALUES (?, ?, ?)
                    """, idempotencyKey, hash, "");
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private String load(String idempotencyKey) {
        RowMapper<String> rows = (rs, row) -> rs.getString(1) + "\n" + rs.getString(2);
        List<String> stored = jdbc.query("""
                SELECT request_hash, response_body FROM up_automation_write_ledger WHERE idempotency_key = ?
                """, rows, idempotencyKey);
        if (stored.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Idempotency record cannot be read");
        }
        return stored.get(0);
    }

    private Map<String, Object> replay(String stored, String hash) {
        int split = stored.indexOf('\n');
        if (!stored.substring(0, split).equals(hash)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Idempotency-Key was used for a different request");
        }
        try {
            return mapper.readValue(stored.substring(split + 1), MAP);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Stored automation response cannot be read");
        }
    }

    private String hash(Map<String, Object> body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json(body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Record cannot be serialized");
        }
    }
}
