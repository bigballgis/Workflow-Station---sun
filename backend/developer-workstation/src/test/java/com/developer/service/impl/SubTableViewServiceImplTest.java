package com.developer.service.impl;

import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.entity.SubTableViewConfig;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubTableViewServiceImplTest {

    @Mock private SubTableViewConfigRepository viewConfigRepository;
    @Mock private FormTableBindingRepository bindingRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;

    private SubTableViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubTableViewServiceImpl(
                viewConfigRepository, bindingRepository, tableDefinitionRepository);
    }

    @Test
    void getViewConfigDtoDoesNotCreateMissingConfig() {
        when(viewConfigRepository.findByBindingId(9L)).thenReturn(Optional.empty());
        var binding = FormTableBinding.builder().id(9L).table(TableDefinition.builder().id(3L).build()).build();
        when(bindingRepository.findByIdWithTable(9L)).thenReturn(Optional.of(binding));
        var dto = service.getViewConfigDTO(9L);
        assertNull(dto.id());
        assertEquals(3L, dto.tableId());
        assertEquals(List.of(), dto.viewFields());
        verify(viewConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(bindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getViewConfigDtoReadsExistingConfig() {
        FormTableBinding binding = new FormTableBinding();
        SubTableViewConfig config = SubTableViewConfig.builder()
                .binding(binding)
                .viewFields(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByBindingId(9L)).thenReturn(Optional.of(config));
        service.getViewConfigDTO(9L);
        verify(viewConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void explicitSaveCreatesPreviouslyUnsetViewAndPersistsColumns() {
        var table = TableDefinition.builder().id(3L).build();
        var binding = FormTableBinding.builder().id(9L).table(table).build();
        when(viewConfigRepository.findByBindingId(9L)).thenReturn(Optional.empty());
        when(bindingRepository.findByIdWithTable(9L)).thenReturn(Optional.of(binding));
        when(tableDefinitionRepository.findById(3L)).thenReturn(Optional.of(table));
        when(viewConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var field = new com.developer.service.SubTableViewService.ViewFieldDTO("amount", "Amount", 100, 0, true);
        var saved = service.saveViewConfig(9L, List.of(field));
        assertEquals(1, saved.getViewFields().size());
        assertEquals("amount", saved.getViewFields().get(0).getFieldName());
        verify(bindingRepository).save(binding);
    }
}
