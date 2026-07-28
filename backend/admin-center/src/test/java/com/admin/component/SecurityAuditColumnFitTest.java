package com.admin.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计行的 varchar 列必须先裁剪再入库。
 *
 * <p>`failure_reason` 是 varchar(500),而上游报错长度不受控——AP 会把整段 pnpm stderr 塞回来。
 * 不裁剪则 INSERT 直接失败,**整条 FAILED 审计记录被丢弃**,只剩一行 WARN;偏偏失败才是审计最该
 * 留下的东西。实测三次 disable 尝试在审计表里一条都没有。
 */
@DisplayName("SecurityAuditComponent — varchar 列裁剪")
class SecurityAuditColumnFitTest {

    private static String fit(String value, int max) {
        return (String) ReflectionTestUtils.invokeMethod(
                SecurityAuditComponent.class, "fitColumn", value, max);
    }

    @Test
    @DisplayName("超长值裁到列宽以内,并留下省略号标记")
    void overlongValueIsTrimmedToFit() {
        String apStderr = "AP webhook failed: " + "x".repeat(5000);

        String out = fit(apStderr, 500);

        assertThat(out).hasSize(500);
        assertThat(out).endsWith("...");
        assertThat(out).startsWith("AP webhook failed:");
    }

    @Test
    @DisplayName("列宽以内的值原样保留,不加省略号")
    void shortValueUntouched() {
        assertThat(fit("HTTP 409: FLOW_IN_USE (CSV Import (AP))", 500))
                .isEqualTo("HTTP 409: FLOW_IN_USE (CSV Import (AP))");
    }

    @Test
    @DisplayName("恰好等于列宽不裁剪——边界不能多砍一刀")
    void exactlyAtLimitUntouched() {
        String exact = "y".repeat(500);
        assertThat(fit(exact, 500)).isEqualTo(exact);
    }

    @Test
    @DisplayName("null 透传,不能变成 \"null\" 字符串")
    void nullStaysNull() {
        assertThat(fit(null, 500)).isNull();
    }
}
