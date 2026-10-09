package com.portal.component;

import java.util.function.Supplier;

/**
 * After a Portal Action succeeds, publishes a post-action email request when the Action
 * has {@code config_json.postEmail.enabled}. Failures never fail the Action.
 */
public interface ActionPostEmailComponent {

    <T> T runWithPostEmail(ActionPostEmailContext context, Supplier<T> action);

    void runWithPostEmail(ActionPostEmailContext context, Runnable action);
}
