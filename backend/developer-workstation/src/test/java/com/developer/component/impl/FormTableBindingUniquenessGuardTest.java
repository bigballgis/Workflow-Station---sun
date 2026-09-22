package com.developer.component.impl;

import com.developer.dto.FormTableBindingRequest;
import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.FormTableBindingRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormTableBindingUniquenessGuardTest {

    private static final long FORM = 10L;
    private static final long TABLE = 20L;

    @Mock private FormTableBindingRepository repository;
    @Mock private I18nService i18nService;

    private FormTableBindingUniquenessGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage("form.binding_exists")).thenReturn("bound");
        lenient().when(i18nService.getMessage("form.no_duplicate_binding")).thenReturn("pick another filter");
        guard = new FormTableBindingUniquenessGuard(repository, i18nService);
    }

    @Test
    void secondSubBindingOnSameTableIsAllowedWhenFilterFkDiffers() {
        when(repository.findByFormIdAndTableId(FORM, TABLE)).thenReturn(List.of(sub(1L, 101L)));

        assertThatCode(() -> guard.assertCreate(FORM, subRequest(), false, 202L))
                .doesNotThrowAnyException();
    }

    @Test
    void secondSubBindingOnSameTableIsRejectedWhenFilterFkMatches() {
        when(repository.findByFormIdAndTableId(FORM, TABLE)).thenReturn(List.of(sub(1L, 101L)));

        assertThatThrownBy(() -> guard.assertCreate(FORM, subRequest(), false, 101L))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BINDING_EXISTS");
    }

    @Test
    void twoUndeclaredFiltersOnTheSameSubTableAreDuplicates() {
        when(repository.findByFormIdAndTableId(FORM, TABLE)).thenReturn(List.of(sub(1L, null)));

        assertThatThrownBy(() -> guard.assertCreate(FORM, subRequest(), false, null))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BINDING_EXISTS");
    }

    @Test
    void nonSubBindingStillRejectsASecondRowOnTheSameTable() {
        when(repository.existsByFormIdAndTableId(FORM, TABLE)).thenReturn(true);

        FormTableBindingRequest request = FormTableBindingRequest.builder()
                .tableId(TABLE)
                .bindingType(BindingType.PRIMARY)
                .build();

        assertThatThrownBy(() -> guard.assertCreate(FORM, request, false, null))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BINDING_EXISTS");
    }

    @Test
    void updateMayKeepItsOwnFilter() {
        FormTableBinding existing = sub(5L, 101L);
        when(repository.findByFormIdAndTableId(FORM, TABLE)).thenReturn(List.of(existing, sub(6L, 202L)));

        assertThatCode(() -> guard.assertUpdate(existing, subRequest(), 101L))
                .doesNotThrowAnyException();
    }

    @Test
    void subBindingIsRejectedWhenANonSubSiblingOwnsTheTable() {
        FormTableBinding primary = FormTableBinding.builder()
                .id(1L)
                .bindingType(BindingType.PRIMARY)
                .build();
        when(repository.findByFormIdAndTableId(FORM, TABLE)).thenReturn(List.of(primary));

        assertThatThrownBy(() -> guard.assertCreate(FORM, subRequest(), false, 101L))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BINDING_EXISTS");
    }

    @Test
    void updateCannotStealASiblingFilter() {
        FormTableBinding existing = sub(5L, 101L);
        when(repository.findByFormIdAndTableId(FORM, TABLE)).thenReturn(List.of(existing, sub(6L, 202L)));

        assertThatThrownBy(() -> guard.assertUpdate(existing, subRequest(), 202L))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BINDING_EXISTS");
    }

    private static FormTableBindingRequest subRequest() {
        return FormTableBindingRequest.builder()
                .tableId(TABLE)
                .bindingType(BindingType.SUB)
                .foreignKeyField("party_id")
                .build();
    }

    private static FormTableBinding sub(Long id, Long filterFkFieldId) {
        FormTableBinding binding = FormTableBinding.builder()
                .id(id)
                .bindingType(BindingType.SUB)
                .filterFkFieldId(filterFkFieldId)
                .build();
        binding.setForm(com.developer.entity.FormDefinition.builder().id(FORM).build());
        binding.setTable(com.developer.entity.TableDefinition.builder().id(TABLE).build());
        return binding;
    }
}
