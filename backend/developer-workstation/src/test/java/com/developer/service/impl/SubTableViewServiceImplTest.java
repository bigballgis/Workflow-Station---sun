package com.developer.service.impl;

import com.developer.entity.FormTableBinding;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertThrows(IllegalArgumentException.class, () -> service.getViewConfigDTO(9L));
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
}
