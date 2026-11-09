package com.developer.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.developer.exception.DeveloperBusinessException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The runtime finds a View by "function unit code + View name", so a clash would make two Views one. */
class MainTableViewNameValidatorTest {

    @Test
    void distinctNamesPass() {
        assertThatCode(() -> MainTableViewNameValidator.assertUnique(List.of("Cases", "Case Lines", "Stages")))
                .doesNotThrowAnyException();
    }

    @Test
    void sameNameIgnoringCaseAndSpacesIsRejectedNamingBothSpellings() {
        assertThatThrownBy(() -> MainTableViewNameValidator.assertUnique(List.of("Cases", "Stages", " cases ")))
                .isInstanceOf(DeveloperBusinessException.class)
                .satisfies(ex -> {
                    DeveloperBusinessException e = (DeveloperBusinessException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(MainTableViewNameValidator.DUPLICATE_CODE);
                    assertThat(e.getMessage()).contains("'Cases'").contains("'cases'");
                });
    }

    /** Names the database cannot hold anyway are not a clash; this check is about identity only. */
    @Test
    void blankNamesAreNotCountedAsAClash() {
        assertThatCode(() -> MainTableViewNameValidator.assertUnique(Arrays.asList(null, "", "  ", null, "Cases")))
                .doesNotThrowAnyException();
    }

    @Test
    void aNameAnotherViewUsesIsNotAvailable() {
        assertThatThrownBy(() -> MainTableViewNameValidator.assertAvailable("CASES", List.of("Stages", "Cases")))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewNameValidator.DUPLICATE_CODE);
    }

    /** Only the saved View's own name matters: a clash between two other Views must not block its save. */
    @Test
    void aFreeNameIsAvailableEvenWhenOtherViewsClashAmongThemselves() {
        assertThatCode(() -> MainTableViewNameValidator.assertAvailable("Stages", List.of("Cases", "cases")))
                .doesNotThrowAnyException();
    }
}
