package com.workflow.component;

/**
 * 数据脱敏规则（原 {@link DataAccessSecurityComponent} 内嵌类型）。
 */
public class DataMaskRule {
    private String ruleId;
    private String ruleName;
    /** PHONE, ID_CARD, EMAIL, BANK_CARD, NAME, ADDRESS, CUSTOM */
    private String dataType;
    private String maskPattern;
    private String replacement;
    private int keepStart;
    private int keepEnd;
    private boolean enabled;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getMaskPattern() {
        return maskPattern;
    }

    public void setMaskPattern(String maskPattern) {
        this.maskPattern = maskPattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public int getKeepStart() {
        return keepStart;
    }

    public void setKeepStart(int keepStart) {
        this.keepStart = keepStart;
    }

    public int getKeepEnd() {
        return keepEnd;
    }

    public void setKeepEnd(int keepEnd) {
        this.keepEnd = keepEnd;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
