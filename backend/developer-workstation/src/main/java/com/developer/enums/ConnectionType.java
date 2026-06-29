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
    /** @deprecated legacy import; treated as GMAIL preset */
    SMTP
}
