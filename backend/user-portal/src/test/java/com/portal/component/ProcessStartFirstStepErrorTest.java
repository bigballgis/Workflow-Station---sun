package com.portal.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首任务自动完成失败的判定与原因提取。
 *
 * <p>{@code /start} 不能因为首步失败而报错——实例已创建、任务退回发起人待办可重试。但它此前
 * 只写一行 WARN 就返回 200，前端照弹「提交成功」，于是引擎侧已经修好的 AP 自动化失败在用户
 * 眼里依然是一次成功提交。这里锁住的是「失败必须带原因回到调用方」。
 */
@DisplayName("ProcessStartComponent — 首步失败原因回传")
class ProcessStartFirstStepErrorTest {

    private static Optional<Map<String, Object>> body(Object success, Object message) {
        Map<String, Object> m = new HashMap<>();
        m.put("success", success);
        m.put("message", message);
        return Optional.of(m);
    }

    @Test
    @DisplayName("success=true → null，成功路径不受影响")
    void successReturnsNull() {
        assertThat(ProcessStartComponent.firstStepErrorOf(body(true, null))).isNull();
    }

    @Test
    @DisplayName("缺少 success 字段视为成功——保持既有语义，只有显式 false 才算失败")
    void missingSuccessFlagIsTreatedAsSuccess() {
        assertThat(ProcessStartComponent.firstStepErrorOf(Optional.of(new HashMap<>()))).isNull();
    }

    @Test
    @DisplayName("success=false → 回传引擎给的原因，用户可据此判断")
    void engineFailurePropagatesMessage() {
        String engineMessage = "Task completion failed: AP flow returned no response (HTTP 204)";

        assertThat(ProcessStartComponent.firstStepErrorOf(body(false, engineMessage)))
                .isEqualTo(engineMessage);
    }

    @Test
    @DisplayName("success=false 但没给 message → 仍要有非空原因，不能悄悄变回成功")
    void blankMessageStillReportsFailure() {
        assertThat(ProcessStartComponent.firstStepErrorOf(body(false, null)))
                .isEqualTo("workflow engine rejected task completion");
        assertThat(ProcessStartComponent.firstStepErrorOf(body(false, "   ")))
                .isEqualTo("workflow engine rejected task completion");
    }

    @Test
    @DisplayName("引擎完全没响应 → 也算失败")
    void emptyResultIsFailure() {
        assertThat(ProcessStartComponent.firstStepErrorOf(Optional.empty()))
                .isEqualTo("no response from workflow engine");
    }

    @Test
    @DisplayName("回给浏览器的是固定标记,不能带 AP webhook URL——那个 URL 无鉴权、等同凭据")
    void clientFacingMarkerLeaksNothing() {
        String marker = ProcessStartComponent.FIRST_STEP_NOT_COMPLETED;

        assertThat(marker)
                .doesNotContain("http")
                .doesNotContain("webhook")
                .doesNotContain("activepieces");
    }
}
