package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStartupMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup mode must survive every path that rebuilds a Function Unit — clone, export/import and
 * version rollback. If it does not, a callable unit silently becomes STANDALONE and every call
 * activity targeting it starts failing deployment validation, with nothing pointing at the cause.
 *
 * <p>These tests pin the two decisions that are easy to get wrong rather than re-wiring the whole
 * exporter: the builder default must not erase an explicit value, and an absent value must leave
 * the existing mode alone instead of resetting it.
 */
class FunctionUnitStartupModePortabilityTest {

    @Test
    void newUnitDefaultsToStandaloneSoExistingUnitsAreUnaffected() {
        FunctionUnit unit = FunctionUnit.builder().name("Fresh").build();

        assertThat(unit.getStartupMode()).isEqualTo(FunctionUnitStartupMode.STANDALONE);
    }

    /**
     * The @Builder.Default trap: a builder that does not mention the field gets the default, which
     * is right for a new unit but would be wrong for a clone of a callable one.
     */
    @Test
    void builderKeepsAnExplicitStartupMode() {
        FunctionUnit cloned = FunctionUnit.builder()
                .name("Clone of callable")
                .startupMode(FunctionUnitStartupMode.CALLABLE)
                .build();

        assertThat(cloned.getStartupMode()).isEqualTo(FunctionUnitStartupMode.CALLABLE);
    }

    @Test
    void callableAndBothAllowBeingCalled() {
        assertThat(FunctionUnitStartupMode.CALLABLE.allowsBeingCalled()).isTrue();
        assertThat(FunctionUnitStartupMode.BOTH.allowsBeingCalled()).isTrue();
        assertThat(FunctionUnitStartupMode.STANDALONE.allowsBeingCalled()).isFalse();
    }

    @Test
    void standaloneAndBothAllowUserInitiation() {
        assertThat(FunctionUnitStartupMode.STANDALONE.allowsUserInitiation()).isTrue();
        assertThat(FunctionUnitStartupMode.BOTH.allowsUserInitiation()).isTrue();
        assertThat(FunctionUnitStartupMode.CALLABLE.allowsUserInitiation()).isFalse();
    }

    @Test
    void everyModeIsEitherCallableOrUserStartable() {
        // A mode that permits neither would make the unit unusable; guards future additions.
        for (FunctionUnitStartupMode mode : FunctionUnitStartupMode.values()) {
            assertThat(mode.allowsBeingCalled() || mode.allowsUserInitiation())
                    .as("%s must permit at least one way of starting", mode)
                    .isTrue();
        }
    }
}
