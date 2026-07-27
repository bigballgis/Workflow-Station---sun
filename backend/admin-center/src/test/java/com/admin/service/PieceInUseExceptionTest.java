package com.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 删除被占用的 piece 时,冲突信息要给出占用方名称。
 *
 * <p>此前只带引用数,Controller 直接把它当 message 回前端,管理员看到的是一个裸 "1"——
 * 既不知道是哪个 flow 在用,也无从判断能不能删。FLOW_IN_USE 一侧早就回的是 FU 名称,两边对齐。
 */
@DisplayName("PieceInUseException — 冲突信息带占用方名称")
class PieceInUseExceptionTest {

    @Test
    @DisplayName("携带 flow 名称,Controller 可直接 join 成 message")
    void carriesFlowNames() {
        var e = new AutomationPieceService.PieceInUseException(
                "@activepieces/piece-csv", List.of("csv", "Automation flow"));

        assertThat(e.getFlowNames()).containsExactly("csv", "Automation flow");
        assertThat(String.join(", ", e.getFlowNames())).isEqualTo("csv, Automation flow");
    }

    @Test
    @DisplayName("count 由名称列表派生,不再单独维护")
    void countDerivedFromNames() {
        var e = new AutomationPieceService.PieceInUseException("p", List.of("a", "b", "c"));

        assertThat(e.getFlowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("异常自身 message 仍写明是哪个 piece、被几个 flow 占用(给日志看)")
    void messageNamesThePiece() {
        var e = new AutomationPieceService.PieceInUseException("@activepieces/piece-csv", List.of("csv"));

        assertThat(e.getMessage()).contains("@activepieces/piece-csv").contains("1 flow(s)");
    }
}
