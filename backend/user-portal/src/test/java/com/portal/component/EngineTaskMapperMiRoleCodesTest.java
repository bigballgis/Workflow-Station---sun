package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every role of an MI row assigned by role reaches the Portal task, not only the first (WP8a review). */
class EngineTaskMapperMiRoleCodesTest {

    @Test
    void allRolesOfTheRowAreMapped() {
        Map<String, Object> row = new HashMap<>(Map.of("taskId", "t1", "miAssigneeMode", "role",
                "miRoleCodes", List.of("CLERK", "APPROVER"), "miBusinessUnitCode", "FIN"));

        assertThat(EngineTaskMapper.convertMapToTaskInfo(row).getMiRoleCodes()).containsExactly("CLERK", "APPROVER");
    }

    @Test
    void aTaskWithoutRolesHasNone() {
        assertThat(EngineTaskMapper.convertMapToTaskInfo(new HashMap<>(Map.of("taskId", "t2"))).getMiRoleCodes())
                .isNullOrEmpty();
    }
}
