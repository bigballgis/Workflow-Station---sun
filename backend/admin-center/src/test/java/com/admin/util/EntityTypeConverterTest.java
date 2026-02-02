package com.admin.util;

import com.admin.enums.BusinessUnitStatus;
import com.admin.enums.RoleType;
import com.admin.enums.VirtualGroupType;
import com.admin.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntityTypeConverter.
 * Tests bidirectional conversions between String and Enum types,
 * null handling, and error cases.
 */
@DisplayName("EntityTypeConverter Tests")
class EntityTypeConverterTest {
    
    // ========== toRoleType Tests ==========
    
    @Test
    @DisplayName("toRoleType: Should convert BU_BOUNDED string to enum")
    void testToRoleType_BuBounded() {
        RoleType result = EntityTypeConverter.toRoleType("BU_BOUNDED");
        assertEquals(RoleType.BU_BOUNDED, result);
    }
    
    @Test
    @DisplayName("toRoleType: Should convert BU_UNBOUNDED string to enum")
    void testToRoleType_BuUnbounded() {
        RoleType result = EntityTypeConverter.toRoleType("BU_UNBOUNDED");
        assertEquals(RoleType.BU_UNBOUNDED, result);
    }
    
    @Test
    @DisplayName("toRoleType: Should convert ADMIN string to enum")
    void testToRoleType_Admin() {
        RoleType result = EntityTypeConverter.toRoleType("ADMIN");
        assertEquals(RoleType.ADMIN, result);
    }
    
    @Test
    @DisplayName("toRoleType: Should convert DEVELOPER string to enum")
    void testToRoleType_Developer() {
        RoleType result = EntityTypeConverter.toRoleType("DEVELOPER");
        assertEquals(RoleType.DEVELOPER, result);
    }
    
    @Test
    @DisplayName("toRoleType: Should return null for null input")
    void testToRoleType_Null() {
        RoleType result = EntityTypeConverter.toRoleType(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("toRoleType: Should throw IllegalArgumentException for unknown type")
    void testToRoleType_UnknownType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toRoleType("UNKNOWN_TYPE")
        );
        assertTrue(exception.getMessage().contains("Unknown role type"));
        assertTrue(exception.getMessage().contains("UNKNOWN_TYPE"));
    }
    
    @Test
    @DisplayName("toRoleType: Should throw IllegalArgumentException for empty string")
    void testToRoleType_EmptyString() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toRoleType("")
        );
        assertTrue(exception.getMessage().contains("Unknown role type"));
    }
    
    @Test
    @DisplayName("toRoleType: Should be case-sensitive")
    void testToRoleType_CaseSensitive() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toRoleType("bu_bounded")
        );
        assertTrue(exception.getMessage().contains("Unknown role type"));
    }
    
    // ========== fromRoleType Tests ==========
    
    @Test
    @DisplayName("fromRoleType: Should convert BU_BOUNDED enum to string")
    void testFromRoleType_BuBounded() {
        String result = EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED);
        assertEquals("BU_BOUNDED", result);
    }
    
    @Test
    @DisplayName("fromRoleType: Should convert BU_UNBOUNDED enum to string")
    void testFromRoleType_BuUnbounded() {
        String result = EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED);
        assertEquals("BU_UNBOUNDED", result);
    }
    
    @Test
    @DisplayName("fromRoleType: Should convert ADMIN enum to string")
    void testFromRoleType_Admin() {
        String result = EntityTypeConverter.fromRoleType(RoleType.ADMIN);
        assertEquals("ADMIN", result);
    }
    
    @Test
    @DisplayName("fromRoleType: Should convert DEVELOPER enum to string")
    void testFromRoleType_Developer() {
        String result = EntityTypeConverter.fromRoleType(RoleType.DEVELOPER);
        assertEquals("DEVELOPER", result);
    }
    
    @Test
    @DisplayName("fromRoleType: Should return null for null input")
    void testFromRoleType_Null() {
        String result = EntityTypeConverter.fromRoleType(null);
        assertNull(result);
    }
    
    // ========== Bidirectional Conversion Tests ==========
    
    @Test
    @DisplayName("Bidirectional: toRoleType(fromRoleType(x)) should equal x for all enum values")
    void testBidirectionalConversion_AllEnumValues() {
        for (RoleType roleType : RoleType.values()) {
            String stringType = EntityTypeConverter.fromRoleType(roleType);
            RoleType convertedBack = EntityTypeConverter.toRoleType(stringType);
            assertEquals(roleType, convertedBack, 
                "Bidirectional conversion failed for " + roleType);
        }
    }
    
    @Test
    @DisplayName("Bidirectional: fromRoleType(toRoleType(x)) should equal x for all valid strings")
    void testBidirectionalConversion_AllValidStrings() {
        String[] validStrings = {"BU_BOUNDED", "BU_UNBOUNDED", "ADMIN", "DEVELOPER"};
        
        for (String stringType : validStrings) {
            RoleType enumType = EntityTypeConverter.toRoleType(stringType);
            String convertedBack = EntityTypeConverter.fromRoleType(enumType);
            assertEquals(stringType, convertedBack,
                "Bidirectional conversion failed for " + stringType);
        }
    }
    
    @Test
    @DisplayName("Bidirectional: null should remain null in both directions")
    void testBidirectionalConversion_Null() {
        assertNull(EntityTypeConverter.toRoleType(null));
        assertNull(EntityTypeConverter.fromRoleType(null));
    }
    
    // ========== toVirtualGroupType Tests ==========
    
    @Test
    @DisplayName("toVirtualGroupType: Should convert SYSTEM string to enum")
    void testToVirtualGroupType_System() {
        VirtualGroupType result = EntityTypeConverter.toVirtualGroupType("SYSTEM");
        assertEquals(VirtualGroupType.SYSTEM, result);
    }
    
    @Test
    @DisplayName("toVirtualGroupType: Should convert CUSTOM string to enum")
    void testToVirtualGroupType_Custom() {
        VirtualGroupType result = EntityTypeConverter.toVirtualGroupType("CUSTOM");
        assertEquals(VirtualGroupType.CUSTOM, result);
    }
    
    @Test
    @DisplayName("toVirtualGroupType: Should return null for null input")
    void testToVirtualGroupType_Null() {
        VirtualGroupType result = EntityTypeConverter.toVirtualGroupType(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("toVirtualGroupType: Should throw IllegalArgumentException for unknown type")
    void testToVirtualGroupType_UnknownType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toVirtualGroupType("UNKNOWN_TYPE")
        );
        assertTrue(exception.getMessage().contains("Unknown virtual group type"));
        assertTrue(exception.getMessage().contains("UNKNOWN_TYPE"));
    }
    
    @Test
    @DisplayName("toVirtualGroupType: Should throw IllegalArgumentException for empty string")
    void testToVirtualGroupType_EmptyString() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toVirtualGroupType("")
        );
        assertTrue(exception.getMessage().contains("Unknown virtual group type"));
    }
    
    @Test
    @DisplayName("toVirtualGroupType: Should be case-sensitive")
    void testToVirtualGroupType_CaseSensitive() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toVirtualGroupType("system")
        );
        assertTrue(exception.getMessage().contains("Unknown virtual group type"));
    }
    
    // ========== fromVirtualGroupType Tests ==========
    
    @Test
    @DisplayName("fromVirtualGroupType: Should convert SYSTEM enum to string")
    void testFromVirtualGroupType_System() {
        String result = EntityTypeConverter.fromVirtualGroupType(VirtualGroupType.SYSTEM);
        assertEquals("SYSTEM", result);
    }
    
    @Test
    @DisplayName("fromVirtualGroupType: Should convert CUSTOM enum to string")
    void testFromVirtualGroupType_Custom() {
        String result = EntityTypeConverter.fromVirtualGroupType(VirtualGroupType.CUSTOM);
        assertEquals("CUSTOM", result);
    }
    
    @Test
    @DisplayName("fromVirtualGroupType: Should return null for null input")
    void testFromVirtualGroupType_Null() {
        String result = EntityTypeConverter.fromVirtualGroupType(null);
        assertNull(result);
    }
    
    // ========== VirtualGroupType Bidirectional Conversion Tests ==========
    
    @Test
    @DisplayName("Bidirectional: toVirtualGroupType(fromVirtualGroupType(x)) should equal x for all enum values")
    void testVirtualGroupTypeBidirectionalConversion_AllEnumValues() {
        for (VirtualGroupType groupType : VirtualGroupType.values()) {
            String stringType = EntityTypeConverter.fromVirtualGroupType(groupType);
            VirtualGroupType convertedBack = EntityTypeConverter.toVirtualGroupType(stringType);
            assertEquals(groupType, convertedBack, 
                "Bidirectional conversion failed for " + groupType);
        }
    }
    
    @Test
    @DisplayName("Bidirectional: fromVirtualGroupType(toVirtualGroupType(x)) should equal x for all valid strings")
    void testVirtualGroupTypeBidirectionalConversion_AllValidStrings() {
        String[] validStrings = {"SYSTEM", "CUSTOM"};
        
        for (String stringType : validStrings) {
            VirtualGroupType enumType = EntityTypeConverter.toVirtualGroupType(stringType);
            String convertedBack = EntityTypeConverter.fromVirtualGroupType(enumType);
            assertEquals(stringType, convertedBack,
                "Bidirectional conversion failed for " + stringType);
        }
    }
    
    @Test
    @DisplayName("Bidirectional: null should remain null in both directions for VirtualGroupType")
    void testVirtualGroupTypeBidirectionalConversion_Null() {
        assertNull(EntityTypeConverter.toVirtualGroupType(null));
        assertNull(EntityTypeConverter.fromVirtualGroupType(null));
    }
    
    // ========== toBusinessUnitStatus Tests ==========
    
    @Test
    @DisplayName("toBusinessUnitStatus: Should convert ACTIVE string to enum")
    void testToBusinessUnitStatus_Active() {
        BusinessUnitStatus result = EntityTypeConverter.toBusinessUnitStatus("ACTIVE");
        assertEquals(BusinessUnitStatus.ACTIVE, result);
    }
    
    @Test
    @DisplayName("toBusinessUnitStatus: Should convert DISABLED string to enum")
    void testToBusinessUnitStatus_Disabled() {
        BusinessUnitStatus result = EntityTypeConverter.toBusinessUnitStatus("DISABLED");
        assertEquals(BusinessUnitStatus.DISABLED, result);
    }
    
    @Test
    @DisplayName("toBusinessUnitStatus: Should return null for null input")
    void testToBusinessUnitStatus_Null() {
        BusinessUnitStatus result = EntityTypeConverter.toBusinessUnitStatus(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("toBusinessUnitStatus: Should throw IllegalArgumentException for unknown status")
    void testToBusinessUnitStatus_UnknownStatus() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toBusinessUnitStatus("UNKNOWN_STATUS")
        );
        assertTrue(exception.getMessage().contains("Unknown business unit status"));
        assertTrue(exception.getMessage().contains("UNKNOWN_STATUS"));
    }
    
    @Test
    @DisplayName("toBusinessUnitStatus: Should throw IllegalArgumentException for empty string")
    void testToBusinessUnitStatus_EmptyString() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toBusinessUnitStatus("")
        );
        assertTrue(exception.getMessage().contains("Unknown business unit status"));
    }
    
    @Test
    @DisplayName("toBusinessUnitStatus: Should be case-sensitive")
    void testToBusinessUnitStatus_CaseSensitive() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EntityTypeConverter.toBusinessUnitStatus("active")
        );
        assertTrue(exception.getMessage().contains("Unknown business unit status"));
    }
    
    // ========== fromBusinessUnitStatus Tests ==========
    
    @Test
    @DisplayName("fromBusinessUnitStatus: Should convert ACTIVE enum to string")
    void testFromBusinessUnitStatus_Active() {
        String result = EntityTypeConverter.fromBusinessUnitStatus(BusinessUnitStatus.ACTIVE);
        assertEquals("ACTIVE", result);
    }
    
    @Test
    @DisplayName("fromBusinessUnitStatus: Should convert DISABLED enum to string")
    void testFromBusinessUnitStatus_Disabled() {
        String result = EntityTypeConverter.fromBusinessUnitStatus(BusinessUnitStatus.DISABLED);
        assertEquals("DISABLED", result);
    }
    
    @Test
    @DisplayName("fromBusinessUnitStatus: Should return null for null input")
    void testFromBusinessUnitStatus_Null() {
        String result = EntityTypeConverter.fromBusinessUnitStatus(null);
        assertNull(result);
    }
    
    // ========== BusinessUnitStatus Bidirectional Conversion Tests ==========
    
    @Test
    @DisplayName("Bidirectional: toBusinessUnitStatus(fromBusinessUnitStatus(x)) should equal x for all enum values")
    void testBusinessUnitStatusBidirectionalConversion_AllEnumValues() {
        for (BusinessUnitStatus status : BusinessUnitStatus.values()) {
            String stringStatus = EntityTypeConverter.fromBusinessUnitStatus(status);
            BusinessUnitStatus convertedBack = EntityTypeConverter.toBusinessUnitStatus(stringStatus);
            assertEquals(status, convertedBack, 
                "Bidirectional conversion failed for " + status);
        }
    }
    
    @Test
    @DisplayName("Bidirectional: fromBusinessUnitStatus(toBusinessUnitStatus(x)) should equal x for all valid strings")
    void testBusinessUnitStatusBidirectionalConversion_AllValidStrings() {
        String[] validStrings = {"ACTIVE", "DISABLED"};
        
        for (String stringStatus : validStrings) {
            BusinessUnitStatus enumStatus = EntityTypeConverter.toBusinessUnitStatus(stringStatus);
            String convertedBack = EntityTypeConverter.fromBusinessUnitStatus(enumStatus);
            assertEquals(stringStatus, convertedBack,
                "Bidirectional conversion failed for " + stringStatus);
        }
    }
    
    @Test
    @DisplayName("Bidirectional: null should remain null in both directions for BusinessUnitStatus")
    void testBusinessUnitStatusBidirectionalConversion_Null() {
        assertNull(EntityTypeConverter.toBusinessUnitStatus(null));
        assertNull(EntityTypeConverter.fromBusinessUnitStatus(null));
    }
    
    // ========== toUserStatus Tests ==========
    
    @Test
    @DisplayName("toUserStatus: Should convert platform ACTIVE to admin ACTIVE")
    void testToUserStatus_Active() {
        UserStatus result = EntityTypeConverter.toUserStatus(
            com.platform.security.model.UserStatus.ACTIVE
        );
        assertEquals(UserStatus.ACTIVE, result);
    }
    
    @Test
    @DisplayName("toUserStatus: Should convert platform INACTIVE to admin DISABLED")
    void testToUserStatus_InactiveToDisabled() {
        UserStatus result = EntityTypeConverter.toUserStatus(
            com.platform.security.model.UserStatus.INACTIVE
        );
        assertEquals(UserStatus.DISABLED, result,
            "Platform-security INACTIVE should map to admin-center DISABLED");
    }
    
    @Test
    @DisplayName("toUserStatus: Should convert platform LOCKED to admin LOCKED")
    void testToUserStatus_Locked() {
        UserStatus result = EntityTypeConverter.toUserStatus(
            com.platform.security.model.UserStatus.LOCKED
        );
        assertEquals(UserStatus.LOCKED, result);
    }
    
    @Test
    @DisplayName("toUserStatus: Should return null for null input")
    void testToUserStatus_Null() {
        UserStatus result = EntityTypeConverter.toUserStatus(null);
        assertNull(result);
    }
    
    // ========== fromUserStatus Tests ==========
    
    @Test
    @DisplayName("fromUserStatus: Should convert admin ACTIVE to platform ACTIVE")
    void testFromUserStatus_Active() {
        com.platform.security.model.UserStatus result = EntityTypeConverter.fromUserStatus(
            UserStatus.ACTIVE
        );
        assertEquals(com.platform.security.model.UserStatus.ACTIVE, result);
    }
    
    @Test
    @DisplayName("fromUserStatus: Should convert admin DISABLED to platform INACTIVE")
    void testFromUserStatus_DisabledToInactive() {
        com.platform.security.model.UserStatus result = EntityTypeConverter.fromUserStatus(
            UserStatus.DISABLED
        );
        assertEquals(com.platform.security.model.UserStatus.INACTIVE, result,
            "Admin-center DISABLED should map to platform-security INACTIVE");
    }
    
    @Test
    @DisplayName("fromUserStatus: Should convert admin LOCKED to platform LOCKED")
    void testFromUserStatus_Locked() {
        com.platform.security.model.UserStatus result = EntityTypeConverter.fromUserStatus(
            UserStatus.LOCKED
        );
        assertEquals(com.platform.security.model.UserStatus.LOCKED, result);
    }
    
    @Test
    @DisplayName("fromUserStatus: Should convert admin PENDING to platform INACTIVE")
    void testFromUserStatus_PendingToInactive() {
        com.platform.security.model.UserStatus result = EntityTypeConverter.fromUserStatus(
            UserStatus.PENDING
        );
        assertEquals(com.platform.security.model.UserStatus.INACTIVE, result,
            "Admin-center PENDING should map to platform-security INACTIVE");
    }
    
    @Test
    @DisplayName("fromUserStatus: Should return null for null input")
    void testFromUserStatus_Null() {
        com.platform.security.model.UserStatus result = EntityTypeConverter.fromUserStatus(null);
        assertNull(result);
    }
    
    // ========== UserStatus Special Mapping Tests ==========
    
    @Test
    @DisplayName("UserStatus: DISABLED and PENDING both map to INACTIVE")
    void testUserStatus_MultipleAdminStatusesToSamePlatformStatus() {
        com.platform.security.model.UserStatus disabledResult = 
            EntityTypeConverter.fromUserStatus(UserStatus.DISABLED);
        com.platform.security.model.UserStatus pendingResult = 
            EntityTypeConverter.fromUserStatus(UserStatus.PENDING);
        
        assertEquals(com.platform.security.model.UserStatus.INACTIVE, disabledResult);
        assertEquals(com.platform.security.model.UserStatus.INACTIVE, pendingResult);
        assertEquals(disabledResult, pendingResult,
            "Both DISABLED and PENDING should map to the same platform status INACTIVE");
    }
    
    @Test
    @DisplayName("UserStatus: INACTIVE always maps to DISABLED (not PENDING)")
    void testUserStatus_InactiveAlwaysMapsToDisabled() {
        UserStatus result = EntityTypeConverter.toUserStatus(
            com.platform.security.model.UserStatus.INACTIVE
        );
        assertEquals(UserStatus.DISABLED, result,
            "Platform INACTIVE should always map to admin DISABLED, not PENDING");
    }
    
    @Test
    @DisplayName("UserStatus: Round-trip conversion for ACTIVE preserves value")
    void testUserStatus_RoundTripActive() {
        UserStatus original = UserStatus.ACTIVE;
        com.platform.security.model.UserStatus platform = EntityTypeConverter.fromUserStatus(original);
        UserStatus roundTrip = EntityTypeConverter.toUserStatus(platform);
        assertEquals(original, roundTrip);
    }
    
    @Test
    @DisplayName("UserStatus: Round-trip conversion for LOCKED preserves value")
    void testUserStatus_RoundTripLocked() {
        UserStatus original = UserStatus.LOCKED;
        com.platform.security.model.UserStatus platform = EntityTypeConverter.fromUserStatus(original);
        UserStatus roundTrip = EntityTypeConverter.toUserStatus(platform);
        assertEquals(original, roundTrip);
    }
    
    @Test
    @DisplayName("UserStatus: Round-trip conversion for DISABLED maps to DISABLED (via INACTIVE)")
    void testUserStatus_RoundTripDisabled() {
        UserStatus original = UserStatus.DISABLED;
        com.platform.security.model.UserStatus platform = EntityTypeConverter.fromUserStatus(original);
        UserStatus roundTrip = EntityTypeConverter.toUserStatus(platform);
        
        assertEquals(com.platform.security.model.UserStatus.INACTIVE, platform,
            "DISABLED should convert to INACTIVE");
        assertEquals(UserStatus.DISABLED, roundTrip,
            "INACTIVE should convert back to DISABLED");
    }
    
    @Test
    @DisplayName("UserStatus: Round-trip conversion for PENDING loses information (becomes DISABLED)")
    void testUserStatus_RoundTripPendingLosesInformation() {
        UserStatus original = UserStatus.PENDING;
        com.platform.security.model.UserStatus platform = EntityTypeConverter.fromUserStatus(original);
        UserStatus roundTrip = EntityTypeConverter.toUserStatus(platform);
        
        assertEquals(com.platform.security.model.UserStatus.INACTIVE, platform,
            "PENDING should convert to INACTIVE");
        assertEquals(UserStatus.DISABLED, roundTrip,
            "INACTIVE converts back to DISABLED, not PENDING - information is lost");
        assertNotEquals(original, roundTrip,
            "PENDING cannot be preserved through round-trip conversion");
    }
    
    @Test
    @DisplayName("UserStatus: All platform statuses can be converted to admin statuses")
    void testUserStatus_AllPlatformStatusesConvertable() {
        for (com.platform.security.model.UserStatus platformStatus : 
                com.platform.security.model.UserStatus.values()) {
            assertDoesNotThrow(() -> {
                UserStatus adminStatus = EntityTypeConverter.toUserStatus(platformStatus);
                assertNotNull(adminStatus, 
                    "Platform status " + platformStatus + " should convert to a non-null admin status");
            });
        }
    }
    
    @Test
    @DisplayName("UserStatus: All admin statuses can be converted to platform statuses")
    void testUserStatus_AllAdminStatusesConvertable() {
        for (UserStatus adminStatus : UserStatus.values()) {
            assertDoesNotThrow(() -> {
                com.platform.security.model.UserStatus platformStatus = 
                    EntityTypeConverter.fromUserStatus(adminStatus);
                assertNotNull(platformStatus,
                    "Admin status " + adminStatus + " should convert to a non-null platform status");
            });
        }
    }
}
