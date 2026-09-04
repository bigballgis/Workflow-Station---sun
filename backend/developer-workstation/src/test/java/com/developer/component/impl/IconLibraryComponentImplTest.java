package com.developer.component.impl;

import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IconLibraryComponentImplTest {

    private static final byte[] SAMPLE_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @Mock
    private IconRepository iconRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    private IconLibraryComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new IconLibraryComponentImpl(iconRepository, functionUnitRepository);
    }

    @Test
    void upload_sameFilenameTwice_createsIndependentIcons() {
        AtomicLong ids = new AtomicLong(1L);
        when(iconRepository.save(any(Icon.class))).thenAnswer(invocation -> {
            Icon icon = invocation.getArgument(0);
            icon.setId(ids.getAndIncrement());
            return icon;
        });

        MockMultipartFile firstFile = sampleSvg("sample.svg");
        MockMultipartFile secondFile = sampleSvg("sample.svg");

        Icon first = component.upload(firstFile, "sample", IconCategory.GENERAL, null);
        Icon second = component.upload(secondFile, "sample", IconCategory.GENERAL, null);

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
        assertThat(first.getName()).isEqualTo("sample");
        assertThat(second.getName()).isEqualTo("sample");
        verify(iconRepository, times(2)).save(any(Icon.class));
        verify(iconRepository, never()).existsByName(any());
        verify(iconRepository, never()).findFirstByNameOrderByIdAsc(any());
    }

    private static MockMultipartFile sampleSvg(String filename) {
        return new MockMultipartFile("file", filename, "image/svg+xml", SAMPLE_SVG);
    }
}
