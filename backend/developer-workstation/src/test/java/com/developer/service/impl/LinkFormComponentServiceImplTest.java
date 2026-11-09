package com.developer.service.impl;

import com.developer.dto.LinkFormDataRequest;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.LinkFormComponentRepository;
import com.developer.repository.LinkFormDataRepository;
import com.developer.security.WorkspaceRouteOwnership;
import com.developer.security.WorkspaceRouteOwnership.Owned;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

/** A link-form component id from the body / query must be a component of the unit in the path (§6.1, §6.2). */
@ExtendWith(MockitoExtension.class)
class LinkFormComponentServiceImplTest {

    @Mock private LinkFormComponentRepository componentRepository;
    @Mock private LinkFormDataRepository dataRepository;
    @Mock private FormDefinitionRepository formRepository;
    @Mock private WorkspaceRouteOwnership routeOwnership;

    private LinkFormComponentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LinkFormComponentServiceImpl(componentRepository, dataRepository, formRepository,
                new ObjectMapper(), routeOwnership);
        doThrow(new ResourceNotFoundException("LinkFormComponent", "9")).when(routeOwnership)
                .requireInUnit(Owned.LINK_FORM_COMPONENT, 9L, 1L);
    }

    @Test
    void saveFormData_componentOfAnotherUnit_isNotFoundAndNothingIsSaved() {
        LinkFormDataRequest request = LinkFormDataRequest.builder().componentId(9L).subTableRowId(5L).build();

        assertThrows(ResourceNotFoundException.class, () -> service.saveFormData(1L, request));
        verifyNoInteractions(dataRepository);
    }

    @Test
    void getFormData_componentOfAnotherUnit_isNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getFormData(1L, 9L, 5L));
        verifyNoInteractions(dataRepository);
    }
}
