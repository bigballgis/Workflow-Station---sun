package com.admin.ldap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * TLS sockets of LDAP connections when {@code ldap.keystore-path} names a truststore: LDAP trusts the certificates in
 * it, and every other outbound TLS connection of Admin Center keeps the JVM's default trust.
 *
 * <p>JNDI creates the factory from its class name ({@code java.naming.ldap.factory.socket}) through the static
 * {@link #getDefault()}, so the trust it hands out is set once, when {@link LdapClient} starts. Host names are still
 * verified: JNDI applies LDAPS endpoint identification to any TLS socket it is given.</p>
 */
public final class LdapTrustStoreSocketFactory extends SSLSocketFactory {

    private static volatile SSLSocketFactory trusted;

    private final SSLSocketFactory delegate;

    private LdapTrustStoreSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    /** Called by JNDI for each LDAP connection. */
    public static SocketFactory getDefault() {
        SSLSocketFactory factory = trusted;
        if (factory == null) {
            throw new IllegalStateException("The LDAP truststore has not been loaded");
        }
        return new LdapTrustStoreSocketFactory(factory);
    }

    /**
     * Reads the truststore (in the JVM's default keystore format, as {@code javax.net.ssl.trustStore} would be) and
     * makes LDAP connections trust what it holds.
     *
     * @throws IOException              the file cannot be read, or the password does not open it
     * @throws GeneralSecurityException the file is not a truststore of that format
     */
    static void load(String path, String password) throws IOException, GeneralSecurityException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            trustStore.load(in, password == null || password.isEmpty() ? null : password.toCharArray());
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        trusted = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return delegate.createSocket();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return delegate.createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}
