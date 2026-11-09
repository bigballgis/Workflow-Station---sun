package com.platform.common.security;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Set;

/**
 * HTTP client for outbound calls to URLs that {@link SsrfProtection} guards (user-configured webhooks, the AI
 * gateway). Every connection resolves its host through {@link SsrfProtection#resolveValidated}, and connects to the
 * addresses that check returned: there is no second lookup an attacker's DNS could answer differently (rebinding),
 * whether the call happens right after the URL was validated or minutes later on a retry.
 *
 * <p>Cookies are not kept between calls and redirects are not followed (a redirect would be a request to a URL
 * nobody validated). Needs Apache HttpClient 5 on the classpath of the module that calls it.</p>
 */
public final class SsrfGuardedHttpClient {

    private SsrfGuardedHttpClient() {}

    /**
     * @param allowedHosts hosts that may resolve to internal addresses (the same list given to
     *                     {@link SsrfProtection#validate(String, Set)})
     */
    public static ClientHttpRequestFactory requestFactory(Set<String> allowedHosts, Duration connectTimeout,
                                                          Duration readTimeout) {
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver(allowedHosts))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .setSocketTimeout(Timeout.of(readTimeout))
                                .build())
                        .build())
                .disableCookieManagement()
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
        // The read timeout is the socket timeout of the connection config above.
        return new HttpComponentsClientHttpRequestFactory(client);
    }

    static DnsResolver dnsResolver(Set<String> allowedHosts) {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                try {
                    return SsrfProtection.resolveValidated(host, allowedHosts);
                } catch (SsrfProtection.SsrfException e) {
                    UnknownHostException refused = new UnknownHostException(host + ": " + e.getMessage());
                    refused.initCause(e);
                    throw refused;
                }
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }
}
