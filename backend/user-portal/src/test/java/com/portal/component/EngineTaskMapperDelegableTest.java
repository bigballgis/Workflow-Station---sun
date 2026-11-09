package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The task page offers "Delegate" from the engine's own answer, absent means not delegable (D-56). */
class EngineTaskMapperDelegableTest {

    @Test
    void delegableComesFromTheEngine() {
        Map<String, Object> delegable = new HashMap<>(Map.of("taskId", "t1", "delegable", true));
        Map<String, Object> plain = new HashMap<>(Map.of("taskId", "t2"));

        assertThat(EngineTaskMapper.convertMapToTaskInfo(delegable).isDelegable()).isTrue();
        assertThat(EngineTaskMapper.convertMapToTaskInfo(plain).isDelegable()).isFalse();
    }
}
