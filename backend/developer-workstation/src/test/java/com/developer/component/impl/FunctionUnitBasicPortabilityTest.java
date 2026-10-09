package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.IconRepository;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionUnitBasicPortabilityTest {
    private final IconRepository icons = mock(IconRepository.class);
    private final FunctionUnitBasicPortability portability = new FunctionUnitBasicPortability(icons);

    @Test void restoreReusesOnlyExactContentAndClearsExplicitEmptyMetadata() {
        var fu = FunctionUnit.builder().tags(List.of("old")).build();
        var icon = Icon.builder().id(101L).name("QA").category(IconCategory.GENERAL).svgContent("<svg/>").build();
        when(icons.findFirstByNameAndCategoryAndSvgContentOrderByIdAsc("QA", IconCategory.GENERAL, "<svg/>"))
                .thenReturn(Optional.of(icon));
        portability.restore(fu, Map.of("tags", List.of("new"), "icon", FunctionUnitBasicPortability.iconData(icon)));
        assertSame(icon, fu.getIcon()); assertEquals(List.of("new"), fu.getTags());
        var cleared = new LinkedHashMap<String, Object>(); cleared.put("tags", List.of()); cleared.put("icon", null);
        portability.restore(fu, cleared); assertNull(fu.getIcon()); assertEquals(List.of(), fu.getTags());
        verify(icons, never()).save(any());
    }

    @Test void legacyAbsenceDoesNotClearCurrentMetadata() {
        var icon = Icon.builder().id(2L).build();
        var fu = FunctionUnit.builder().tags(List.of("keep")).icon(icon).build();
        portability.restore(fu, Map.of());
        assertSame(icon, fu.getIcon()); assertEquals(List.of("keep"), fu.getTags()); verifyNoInteractions(icons);
    }

    @Test void importCreatesPortableIconWithoutTrustingSourceDatabaseId() {
        when(icons.save(any())).thenAnswer(inv -> { Icon result = inv.getArgument(0); result.setId(101L); return result; });
        var fu = FunctionUnit.builder().build();
        portability.restore(fu, Map.of("icon", Map.of("id", 2, "name", "QA", "category", "GENERAL", "svgContent", "<svg/>")));
        assertEquals(101L, fu.getIcon().getId()); assertEquals(6, fu.getIcon().getFileSize());
        verify(icons, never()).findById(any());
    }

    @Test void malformedMetadataFailsExplicitly() {
        var fu = FunctionUnit.builder().build();
        assertThrows(DeveloperBusinessException.class, () -> portability.restore(fu, Map.of("tags", "bad")));
        assertThrows(DeveloperBusinessException.class, () -> portability.restore(fu, Map.of("icon", Map.of("name", "QA"))));
        assertThrows(DeveloperBusinessException.class, () -> portability.restore(fu, Map.of("icon",
                Map.of("name", "QA", "category", "INVALID", "svgContent", "<svg/>"))));
    }
}
