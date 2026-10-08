package com.admin.component;

/**
 * A version-pinned Function Unit call names a version that is not deployed here.
 *
 * <p>Its own type so the deployment reporter can pass the message through. Most
 * deployment failures are reported by class name only, deliberately — an internal
 * stack message is not something to show a user. This one is the opposite: it
 * describes a configuration the designer can fix (deploy that version, or re-point
 * the call), and withholding the detail would leave them with nothing to act on.
 */
public class CallActivityPinUnresolvableException extends RuntimeException {

    public CallActivityPinUnresolvableException(String message) {
        super(message);
    }
}
