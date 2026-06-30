package com.developer.enums;

/**
 * Email connection type (extensible for API providers).
 */
public enum ConnectionType {
    GMAIL,
    OUTLOOK,
    YAHOO,
    QQ,
    NETEASE_163,
    /** Custom SMTP — host/port/TLS come from {@link EmailConnectionRequest}. */
    SMTP
}
