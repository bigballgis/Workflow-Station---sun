package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史数据导出结果DTO
 * 包含导出文件的信息和内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryExportResult {

    /**
     * 导出文件名
     */
    private String fileName;

    /**
     * 导出文件内容（Base64编码或直接内容）
     */
    private String fileContent;

    /**
     * 导出格式（CSV, EXCEL, PDF等）
     */
    private String format;

    /**
     * 导出的记录数量
     */
    private Long recordCount;

    /**
     * 导出时间
     */
    private LocalDateTime exportTime;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 导出状态
     */
    private ExportStatus status;

    /**
     * 错误信息（如果导出失败）
     */
    private String errorMessage;

    /**
     * 导出请求的用户ID
     */
    private String exportedBy;

    /**
     * 导出描述
     */
    private String description;

    /**
     * 导出状态枚举
     */
    public enum ExportStatus {
        SUCCESS("导出成功"),
        FAILED("导出失败"),
        IN_PROGRESS("导出中"),
        CANCELLED("已取消");

        private final String description;

        ExportStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 创建成功的导出结果
     */
    public static HistoryExportResult success(String fileName, String fileContent, String format, Long recordCount) {
        return HistoryExportResult.builder()
            .fileName(fileName)
            .fileContent(fileContent)
            .format(format)
            .recordCount(recordCount)
            .exportTime(LocalDateTime.now())
            .status(ExportStatus.SUCCESS)
            .fileSize((long) (fileContent != null ? fileContent.length() : 0))
            .build();
    }

    /**
     * 创建失败的导出结果
     */
    public static HistoryExportResult failure(String errorMessage) {
        return HistoryExportResult.builder()
            .status(ExportStatus.FAILED)
            .errorMessage(errorMessage)
            .exportTime(LocalDateTime.now())
            .build();
    }

    /**
     * 获取格式化的文件大小
     */
    public String getFormattedFileSize() {
        if (fileSize == null || fileSize == 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = fileSize.doubleValue();

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * 检查导出是否成功
     */
    public boolean isSuccess() {
        return status == ExportStatus.SUCCESS;
    }

    /**
     * 检查导出是否失败
     */
    public boolean isFailed() {
        return status == ExportStatus.FAILED;
    }

    /**
     * 检查导出是否正在进行
     */
    public boolean isInProgress() {
        return status == ExportStatus.IN_PROGRESS;
    }

    /**
     * 获取导出结果摘要
     */
    public String getSummary() {
        if (isSuccess()) {
            return String.format("成功导出 %d 条记录到文件 %s (%s)", 
                recordCount != null ? recordCount : 0, fileName, getFormattedFileSize());
        } else if (isFailed()) {
            return String.format("导出失败: %s", errorMessage != null ? errorMessage : "未知错误");
        } else if (isInProgress()) {
            return "导出正在进行中...";
        } else {
            return "导出已取消";
        }
    }
}