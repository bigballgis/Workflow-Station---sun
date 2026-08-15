package com.admin.exception;

/**
 * 调用 Activepieces 时拿不到当前操作人。
 *
 * <p>AP 侧不再有共享账号：每次会话都按<b>真实操作人</b>换取（managed-authn 外部 token），
 * 于是「拿不到操作人」= 无法归属这次操作 ⇒ 直接失败，<b>不得</b>回退到任何共享/合成身份
 * （静默回退会让 AP 审计里的人重新变成一个假身份）。
 *
 * <p>触发场景：UI 路径未认证（正常不会到这里，控制器先做 systemadmin 门禁），
 * 或服务间调用只带了 {@code X-Service-Token} 却没带 {@code X-User-Id}/{@code X-Username}。
 */
public class ServiceTaskActorRequiredException extends AdminBusinessException {

    public ServiceTaskActorRequiredException(String message) {
        super("AP_ACTOR_REQUIRED", message);
    }
}
