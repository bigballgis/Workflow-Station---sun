package com.developer.service.impl;

import com.developer.dto.MainTableViewDtos.CreateMainTableViewRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.MainTableViewField;
import com.developer.entity.TableDefinition;
import com.developer.enums.MainTableViewStatus;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.MainTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.MainTableViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MainTableViewServiceImplTest {

    @Mock
    private MainTableViewConfigRepository viewConfigRepository;
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;

    private MainTableViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MainTableViewServiceImpl(
                viewConfigRepository, functionUnitRepository, tableDefinitionRepository);
    }

    @Test
    void seedDefaultViewIfAbsent_createsMainViewWithFields() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        FieldDefinition field = FieldDefinition.builder()
                .fieldName("title")
                .displayName("Title")
                .sortOrder(0)
                .build();
        TableDefinition main = TableDefinition.builder()
                .id(10L)
                .tableType(TableType.MAIN)
                .fieldDefinitions(new ArrayList<>(List.of(field)))
                .build();

        when(viewConfigRepository.existsByFunctionUnitIdAndIsDefaultTrue(1L)).thenReturn(false);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findById(10L)).thenReturn(Optional.of(main));
        when(viewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaultViewIfAbsent(1L, 10L);

        ArgumentCaptor<MainTableViewConfig> captor = ArgumentCaptor.forClass(MainTableViewConfig.class);
        verify(viewConfigRepository).save(captor.capture());
        MainTableViewConfig saved = captor.getValue();
        assertThat(saved.getViewName()).isEqualTo("Main view");
        assertThat(saved.getIsDefault()).isTrue();
        assertThat(saved.getViewFields()).anyMatch(f -> "title".equals(f.getFieldName()));
        assertThat(saved.getViewFields()).anyMatch(MainTableViewField::getIsSystemField);
    }

    @Test
    void seedDefaultViewIfAbsent_skipsWhenDefaultExists() {
        when(viewConfigRepository.existsByFunctionUnitIdAndIsDefaultTrue(1L)).thenReturn(true);
        service.seedDefaultViewIfAbsent(1L, 10L);
        verify(viewConfigRepository, never()).save(any());
    }

    @Test
    void deleteView_rejectsDefaultView() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        MainTableViewConfig config = MainTableViewConfig.builder()
                .id(5L)
                .functionUnit(fu)
                .isDefault(true)
                .build();
        when(viewConfigRepository.findByIdWithFields(5L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.deleteView(1L, 5L))
                .isInstanceOf(DeveloperBusinessException.class);
    }

    @Test
    void publishViewsForFunctionUnit_setsPublishedStatus() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        MainTableViewConfig view = MainTableViewConfig.builder()
                .id(1L)
                .functionUnit(fu)
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(view));
        when(viewConfigRepository.saveAll(any())).thenReturn(List.of(view));

        service.publishViewsForFunctionUnit(1L);

        assertThat(view.getStatus()).isEqualTo(MainTableViewStatus.PUBLISHED);
    }

    @Test
    void createView_requiresMainTable() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(FunctionUnit.builder().id(1L).build()));
        when(tableDefinitionRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createView(1L, new CreateMainTableViewRequest("Custom")))
                .isInstanceOf(DeveloperBusinessException.class);
    }
}
