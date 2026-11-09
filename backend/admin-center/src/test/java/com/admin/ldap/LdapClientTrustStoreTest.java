package com.admin.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.cert.ManageCertificates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.naming.CommunicationException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code ldap.keystore-path} is the truststore of LDAP connections only. LDAP over TLS trusts its certificates while
 * the JVM-wide trust ({@code javax.net.ssl.trustStore}, the default socket factory) that every other outbound TLS
 * connection of Admin Center uses stays as it was. A truststore that cannot be loaded makes every LDAP connection fail
 * with an error naming it. Runs against an UnboundID in-memory directory listening on LDAPS with a self-signed
 * certificate.
 */
class LdapClientTrustStoreTest {

    private static final String BASE_DN = "dc=example,dc=org";
    private static final String PEOPLE = "ou=people," + BASE_DN;
    private static final String USER = "uid=dana," + PEOPLE;
    private static final String TRUST_PIN = "trust-pin";

    @TempDir
    Path dir;

    private InMemoryDirectoryServer ds;
    private Path trustStore;
    private String trustStorePropertyBefore;

    @BeforeEach
    void setUp() throws Exception {
        trustStorePropertyBefore = System.getProperty("javax.net.ssl.trustStore");
        Path serverKeyStore = dir.resolve("server.p12");
        ByteArrayOutputStream toolOutput = new ByteArrayOutputStream();
        ResultCode generated = ManageCertificates.main(new ByteArrayInputStream(new byte[0]), toolOutput, toolOutput,
                "generate-self-signed-certificate", "--keystore", serverKeyStore.toString(),
                "--keystore-password", "server-pin", "--keystore-type", "PKCS12", "--alias", "server",
                "--subject-dn", "CN=localhost", "--days-valid", "2", "--key-algorithm", "RSA",
                "--key-size-bits", "2048", "--signature-algorithm", "SHA256withRSA",
                "--subject-alternative-name-dns", "localhost");
        assertThat(generated).as(toolOutput.toString()).isEqualTo(ResultCode.SUCCESS);

        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials("cn=admin," + BASE_DN, "admin");
        SSLUtil serverSsl = new SSLUtil(new KeyStoreKeyManager(serverKeyStore.toFile(), "server-pin".toCharArray(),
                "PKCS12", "server"), null);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPSConfig("ldaps", 0,
                serverSsl.createSSLServerSocketFactory()));
        config.setSchema(null);
        ds = new InMemoryDirectoryServer(config);
        ds.startListening();
        ds.add("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: example");
        ds.add("dn: " + PEOPLE, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        ds.add("dn: " + USER, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", "cn: Dana", "uid: dana", "sn: dana", "employeeNumber: 200001");

        trustStore = trustStoreHolding(serverKeyStore);
    }

    @AfterEach
    void tearDown() {
        ds.shutDown(true);
    }

    @Test
    void shouldTrustTheConfiguredTrustStoreForLdapOnly() throws Exception {
        LdapClient client = client("ldaps://localhost:" + ds.getListenPort(), true, trustStore.toString(), TRUST_PIN);

        client.configureTransport();

        assertThat(client.getUserAttributes(USER).orElseThrow().get("employeeNumber")).isEqualTo("200001");
        // the JVM-wide trust other outbound TLS uses is untouched: it still does not trust the LDAP certificate
        assertThat(System.getProperty("javax.net.ssl.trustStore")).isEqualTo(trustStorePropertyBefore);
        assertThatThrownBy(() -> {
            try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                    .createSocket("localhost", ds.getListenPort())) {
                socket.startHandshake();
            }
        }).isInstanceOf(SSLException.class);
    }

    @Test
    void shouldUseTheTrustStore_overLdapWithTlsOnAsWellAsLdaps() throws Exception {
        LdapClient client = client("ldap://localhost:" + ds.getListenPort(), true, trustStore.toString(), TRUST_PIN);

        client.configureTransport();

        assertThat(client.findUserDn("dana")).contains(USER);
    }

    @Test
    void shouldConnect_onAPoolThreadWhoseContextClassLoaderCannotSeeAdminCenterClasses() throws Exception {
        LdapClient client = client("ldaps://localhost:" + ds.getListenPort(), true, trustStore.toString(), TRUST_PIN);
        client.configureTransport();
        ClassLoader platformOnly = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());

        Map<String, String> attrs = CompletableFuture.supplyAsync(() -> {
            Thread.currentThread().setContextClassLoader(platformOnly); // like the system class loader in the jar
            try {
                return client.getUserAttributes(USER).orElseThrow();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, task -> new Thread(task).start()).get(30, TimeUnit.SECONDS);

        assertThat(attrs.get("employeeNumber")).isEqualTo("200001");
    }

    @Test
    void shouldFailEveryLdapConnectionNamingTheTrustStore_whenItCannotBeRead() {
        Path missing = dir.resolve("missing.p12");
        LdapClient client = client("ldaps://localhost:" + ds.getListenPort(), true, missing.toString(), TRUST_PIN);

        client.configureTransport(); // Admin Center still starts

        assertThatThrownBy(() -> client.getUserAttributes(USER))
                .isInstanceOf(CommunicationException.class)
                .hasMessageStartingWith("LDAP truststore " + missing + " could not be loaded: ");
        assertThatThrownBy(() -> client.bindAuthenticate(USER, "secret"))
                .isInstanceOf(CommunicationException.class)
                .hasMessageStartingWith("LDAP truststore " + missing + " could not be loaded: ");
        assertThat(System.getProperty("javax.net.ssl.trustStore")).isEqualTo(trustStorePropertyBefore);
    }

    @Test
    void shouldFailEveryLdapConnection_whenTheTrustStorePasswordIsWrongOrThePathIsADirectory() {
        LdapClient wrongPassword = client("ldaps://localhost:" + ds.getListenPort(), true, trustStore.toString(),
                "not-the-pin");
        LdapClient directory = client("ldaps://localhost:" + ds.getListenPort(), true, dir.toString(), TRUST_PIN);

        wrongPassword.configureTransport();
        directory.configureTransport();

        assertThatThrownBy(() -> wrongPassword.getUserAttributes(USER))
                .isInstanceOf(CommunicationException.class)
                .hasMessageStartingWith("LDAP truststore " + trustStore + " could not be loaded: ");
        assertThatThrownBy(() -> directory.getUserAttributes(USER))
                .isInstanceOf(CommunicationException.class)
                .hasMessageStartingWith("LDAP truststore " + dir + " could not be loaded: ");
    }

    @Test
    void shouldIgnoreTheTrustStore_whenLdapDoesNotUseTls() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials("cn=admin," + BASE_DN, "admin");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0));
        config.setSchema(null);
        InMemoryDirectoryServer cleartext = new InMemoryDirectoryServer(config);
        cleartext.startListening();
        try {
            cleartext.add("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: example");
            LdapClient client = client("ldap://localhost:" + cleartext.getListenPort(), false,
                    dir.resolve("missing.p12").toString(), TRUST_PIN);

            client.configureTransport();

            assertThat(client.getUserAttributes(BASE_DN)).isPresent();
        } finally {
            cleartext.shutDown(true);
        }
    }

    /** A PKCS12 truststore holding only the server's certificate, as an operator would build one from the CA. */
    private Path trustStoreHolding(Path serverKeyStore) throws Exception {
        KeyStore server = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(serverKeyStore)) {
            server.load(in, "server-pin".toCharArray());
        }
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("ldap-ca", server.getCertificate("server"));
        Path path = dir.resolve("ldap-trust.p12");
        try (OutputStream out = Files.newOutputStream(path)) {
            trust.store(out, TRUST_PIN.toCharArray());
        }
        return path;
    }

    private LdapClient client(String url, boolean tls, String keystorePath, String keystorePassword) {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setProviderUrl(url);
        props.setBaseDn(BASE_DN);
        props.setBindDn("cn=admin," + BASE_DN);
        props.setBindPassword("admin");
        props.setTls(tls);
        props.setAllowInsecure(!tls); // cleartext only in the test that runs without TLS
        props.setKeystorePath(keystorePath);
        props.setKeystorePassword(keystorePassword);
        props.setConnectTimeoutMs(5000);
        props.setReadTimeoutMs(5000);
        props.setUserSearchFilter("(objectclass=inetOrgPerson)");
        LdapProperties.Attributes names = props.getAttributes();
        names.setEmployeeId("employeeNumber");
        names.setUid("uid");
        names.setSamAccountName("uid");
        names.setCn("cn");
        return new LdapClient(props);
    }
}
