package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Icon;
import com.developer.entity.TableDefinition;
import com.developer.enums.IconCategory;
import com.developer.enums.TableType;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import com.developer.service.impl.AiWriteServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiWriteService 单元测试
 * 写入流程示例：新建模式、修改模式、Icon 匹配/创建
 */
@ExtendWith(MockitoExtension.class)
class AiWriteServiceTest {

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private IconRepository iconRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AiWriteServiceImpl writeService;

    private FunctionUnit functionUnit;

    @BeforeEach
    void setUp() {
        functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("TestUnit")
                .code("fu-test")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();
    }

    @Test
    void applyGeneratedData_newMode_shouldWriteAllComponents() {
        // NEW mode: no existing data
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .name("Updated Name")
                .description("Updated Desc")
                .tableDefinitions(List.of(Map.of(
                        "tableName", "orders",
                        "tableType", "MAIN",
                        "tableDisplayName", "Order Table",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "BIGINT",
                                "isPrimaryKey", true,
                                "description", "Order identifier",
                                "sortOrder", 1
                        ))
                )))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // Verify save was called
        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());

        FunctionUnit saved = captor.getValue();
        assertEquals("Updated Name", saved.getName());
        assertEquals("Updated Desc", saved.getDisplayName());
        assertFalse(saved.getTableDefinitions().isEmpty(), "Table definitions should be written");
        assertEquals("orders", saved.getTableDefinitions().get(0).getTableName());
        assertEquals("Order identifier",
                saved.getTableDefinitions().get(0).getFieldDefinitions().get(0).getDisplayName());
        // Flush tables first, then forms/actions so generated IDs can be embedded in BPMN bindings.
        verify(entityManager, times(2)).flush();
    }

    @Test
    void applyGeneratedData_modifyMode_shouldDeleteOldAndWriteNew() {
        // MODIFY mode: has existing data
        TableDefinition existingTable = TableDefinition.builder()
                .tableName("old_table")
                .tableType(TableType.MAIN)
                .build();
        functionUnit.getTableDefinitions().add(existingTable);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "new_table",
                        "tableType", "MAIN",
                        "tableDisplayName", "New Table",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "BIGINT",
                                "isPrimaryKey", true,
                                "sortOrder", 1
                        ))
                )))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // In MODIFY mode, entityManager.flush() should be called after clearing and after writeTableDefinitions
        verify(entityManager, atLeast(2)).flush();

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());

        FunctionUnit saved = captor.getValue();
        // Old data should be cleared and new data written
        assertEquals(1, saved.getTableDefinitions().size());
        assertEquals("new_table", saved.getTableDefinitions().get(0).getTableName());
    }

    @Test
    void handleIcon_existingIcon_shouldReuse() {
        Icon existingIcon = Icon.builder()
                .id(10L)
                .name("existing-icon")
                .category(IconCategory.GENERAL)
                .svgContent("<svg/>")
                .build();

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(iconRepository.findByName("existing-icon")).thenReturn(Optional.of(existingIcon));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "existing-icon",
                        "category", "GENERAL",
                        "svgContent", "<svg/>"
                ))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // Should NOT create a new icon
        verify(iconRepository, never()).save(any(Icon.class));

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        assertEquals(existingIcon, captor.getValue().getIcon());
    }

    @Test
    void handleIcon_newIcon_shouldCreate() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(iconRepository.findByName("brand-new-icon")).thenReturn(Optional.empty());

        Icon savedIcon = Icon.builder()
                .id(20L)
                .name("brand-new-icon")
                .category(IconCategory.APPROVAL)
                .svgContent("<svg><circle/></svg>")
                .description("A new icon")
                .build();
        when(iconRepository.save(any(Icon.class))).thenReturn(savedIcon);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "brand-new-icon",
                        "category", "APPROVAL",
                        "svgContent", "<svg><circle/></svg>",
                        "description", "A new icon"
                ))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // Should create a new icon
        verify(iconRepository).save(any(Icon.class));

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        assertEquals(savedIcon, captor.getValue().getIcon());
    }
}
