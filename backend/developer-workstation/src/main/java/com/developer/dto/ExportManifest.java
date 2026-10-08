package com.developer.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出包清单文件结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportManifest {
    
    /**
     * 功能单元名称
     */
    private String name;
    
    /**
     * 功能单元代码
     */
    private String code;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 描述
     */
    private String description;

    /**
     * 该功能单元允许的启动方式（STANDALONE / CALLABLE / BOTH）。
     *
     * <p>必须随包携带：否则导入到新环境后单元会退回 STANDALONE，
     * 所有指向它的 callActivity 在部署校验时都会失败。
     * 旧包没有该字段（null），导入时保持目标单元现有取值。
     */
    private String startupMode;

    /**
     * 导出时间
     */
    private LocalDateTime exportedAt;
    
    /**
     * 导出者
     */
    private String exportedBy;
    
    /**
     * 平台版本
     */
    private String platformVersion;
    
    /**
     * 最低平台版本要求
     */
    private String minPlatformVersion;
    
    /**
     * 组件清单
     */
    private Components components;
    
    /**
     * 依赖列表
     */
    private List<String> dependencies;
    
    /**
     * 图标信息
     */
    private IconInfo icon;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Components {
        private String process;
        private List<String> tables;
        private List<String> forms;
        private List<String> actions;
        private List<String> decisions;
        private List<String> connections;
        private List<String> emailMonitors;
        /** {@code email-templates/template_*.json} — Send Task HTML templates */
        private List<String> emailTemplates;
        /** {@code views/main_table_views.json} when present */
        private String mainTableViews;
        /** {@code documents/requirements.md} / {@code documents/design.md} — the documents that exist */
        private List<String> documents;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IconInfo {
        private String name;
        private String category;
        private String color;
        private String svgContent;
    }
}
