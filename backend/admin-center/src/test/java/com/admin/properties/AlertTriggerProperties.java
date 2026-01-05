package com.admin.properties;

import com.admin.component.SystemMonitorComponent;
import com.admin.entity.AlertRule;
import com.admin.enums.AlertSeverity;
import com.admin.repository.AlertRepository;
import com.admin.repository.AlertRuleRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警触发准确性属性测试
 * 属性 15: 告警触发准确性
 * 验证需求: 需求 8.5, 8.6
 */
class AlertTriggerProperties {
    
    private SystemMonitorComponent component;
    
    @BeforeTry
    void setUp() {
        var ruleRepo = Mockito.mock(AlertRuleRepository.class);
        var alertRepo = Mockito.mock(AlertRepository.class);
        component = new SystemMonitorComponent(ruleRepo, alertRepo);
    }
    
    /**
     * 属性 15.1: GT操作符当值大于阈值时触发
     */
    @Property(tries = 20)
    void gtOperatorTriggersWhenValueGreater(
            @ForAll @DoubleRange(min = 0, max = 100) double threshold,
            @ForAll @DoubleRange(min = 0, max = 200) double value) {
        AlertRule rule = createRule("GT", threshold);
        boolean triggered = component.checkAlertCondition(rule, value);
        assertThat(triggered).isEqualTo(value > threshold);
    }
    
    /**
     * 属性 15.2: LT操作符当值小于阈值时触发
     */
    @Property(tries = 20)
    void ltOperatorTriggersWhenValueLess(
            @ForAll @DoubleRange(min = 0, max = 100) double threshold,
            @ForAll @DoubleRange(min = 0, max = 200) double value) {
        AlertRule rule = createRule("LT", threshold);
        boolean triggered = component.checkAlertCondition(rule, value);
        assertThat(triggered).isEqualTo(value < threshold);
    }
    
    /**
     * 属性 15.3: GTE操作符当值大于等于阈值时触发
     */
    @Property(tries = 20)
    void gteOperatorTriggersWhenValueGreaterOrEqual(
            @ForAll @DoubleRange(min = 0, max = 100) double threshold,
            @ForAll @DoubleRange(min = 0, max = 200) double value) {
        AlertRule rule = createRule("GTE", threshold);
        boolean triggered = component.checkAlertCondition(rule, value);
        assertThat(triggered).isEqualTo(value >= threshold);
    }
    
    /**
     * 属性 15.4: LTE操作符当值小于等于阈值时触发
     */
    @Property(tries = 20)
    void lteOperatorTriggersWhenValueLessOrEqual(
            @ForAll @DoubleRange(min = 0, max = 100) double threshold,
            @ForAll @DoubleRange(min = 0, max = 200) double value) {
        AlertRule rule = createRule("LTE", threshold);
        boolean triggered = component.checkAlertCondition(rule, value);
        assertThat(triggered).isEqualTo(value <= threshold);
    }
    
    /**
     * 属性 15.5: 无阈值规则不触发告警
     */
    @Property(tries = 20)
    void noThresholdNeverTriggers(@ForAll @DoubleRange(min = 0, max = 200) double value) {
        AlertRule rule = AlertRule.builder()
                .operator("GT")
                .threshold(null)
                .build();
        boolean triggered = component.checkAlertCondition(rule, value);
        assertThat(triggered).isFalse();
    }
    
    private AlertRule createRule(String operator, double threshold) {
        return AlertRule.builder()
                .id("test-rule")
                .name("Test Rule")
                .operator(operator)
                .threshold(threshold)
                .severity(AlertSeverity.WARNING)
                .enabled(true)
                .build();
    }
}
