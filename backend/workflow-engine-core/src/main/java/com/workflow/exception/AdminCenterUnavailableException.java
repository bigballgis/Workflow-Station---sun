package com.workflow.exception;

/**
 * admin-center 传输层故障（超时/连接拒绝/5xx/熔断）。
 *
 * <p>语义上区别于"查无数据"：抛出本异常表示远端服务当前不可用，结果未知、可重试；
 * 各方法的 null/空集合返回值保留给"确实不存在该数据"（如 404、2xx 空 body）。
 * 分派链路（TaskAssigneeResolver / TaskAssignmentListener / TaskOrphanRepairService）
 * 依赖该区分决定"留痕等待自动补分派"还是"按配置错误处理"。
 */
public class AdminCenterUnavailableException extends RuntimeException {

    public AdminCenterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
