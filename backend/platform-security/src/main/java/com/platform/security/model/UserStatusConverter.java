package com.platform.security.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for UserStatus enum.
 *
 * <p>Handles database values that may contain frontend-variant status strings
 * (e.g. "DISABLED") by normalizing them to the canonical enum constants.
 * This prevents {@code Enum.valueOf} failures when JPA loads entities whose
 * status column was written by admin-center's own UserStatus enum.</p>
 *
 * <p>Read path (DB → Entity):  "DISABLED"/"PENDING" → {@link UserStatus#INACTIVE}</p>
 * <p>Write path (Entity → DB): always writes canonical {@link UserStatus#name()} (e.g. "INACTIVE")</p>
 */
@Converter(autoApply = false)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Use the same tolerant resolution as the JSON/request-param path
        return UserStatus.fromString(dbData);
    }
}
