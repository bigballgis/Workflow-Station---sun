package com.developer.service.impl;

import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingType;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationLookupConfigRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.security.WorkspaceRouteOwnership;
import com.developer.security.WorkspaceRouteOwnership.Owned;
import com.developer.service.RelationLookupService.LookupConfigDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A lookup's view config id comes from the body and must be a view of the form's unit (§6.1, §6.2). */
@ExtendWith(MockitoExtension.class)
class RelationLookupServiceImplTest {

    private static final long FORM_ID = 7L;
    private static final long UNIT = 3L;
    private static final long RELATION_TABLE = 40L;
    private static final long FOREIGN_VIEW = 900L;

    @Mock private RelationLookupConfigRepository lookupConfigRepository;
    @Mock private FormTableBindingRepository formTableBindingRepository;
    @Mock private RelationViewConfigRepository viewConfigRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private WorkspaceRouteOwnership routeOwnership;

    @Test
    void saveLookupConfig_viewConfigOfAnotherUnit_isNotFoundAndNothingIsSaved() {
        FormTableBinding binding = new FormTableBinding();
        binding.setRelationTableId(RELATION_TABLE);
        when(formTableBindingRepository.findByFormIdAndBindingTypeList(FORM_ID, BindingType.RELATED))
                .thenReturn(List.of(binding));
        when(routeOwnership.unitOf(Owned.FORM, FORM_ID)).thenReturn(UNIT);
        doThrow(new ResourceNotFoundException("RelationViewConfig", String.valueOf(FOREIGN_VIEW)))
                .when(routeOwnership).requireInUnit(Owned.RELATION_VIEW_CONFIG, FOREIGN_VIEW, UNIT);
        RelationLookupServiceImpl service = new RelationLookupServiceImpl(lookupConfigRepository,
                formTableBindingRepository, viewConfigRepository, jdbcTemplate, routeOwnership);

        assertThrows(ResourceNotFoundException.class, () -> service.saveLookupConfig(FORM_ID, "lookup_1",
                new LookupConfigDTO(FOREIGN_VIEW, RELATION_TABLE, "name", "name")));
        verifyNoInteractions(lookupConfigRepository);
    }
}
