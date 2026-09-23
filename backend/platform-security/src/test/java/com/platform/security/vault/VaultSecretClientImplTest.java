package com.platform.security.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VaultSecretClientImplTest {

    private static final String KV_BODY =
            "{\"data\":{\"data\":{\"password\":\"auth-code\"},\"metadata\":{\"version\":1}}}";

    @Test
    void encodedPath_rejectsDotDotSegment() {
        assertThatThrownBy(() -> VaultSecretClientImpl.encodedPath("../etc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_missingRestTemplateBean_failsFast() {
        assertThatThrownBy(() -> new VaultSecretClientImpl(emptyRestTemplateProvider(), staticTokenSettings()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VaultSecretClientImpl.REST_TEMPLATE_BEAN);
    }

    @Test
    void readPassword_illegalPath_throwsNotFound() {
        VaultSecretClientImpl client = new VaultSecretClientImpl(new RestTemplate(), staticTokenSettings());
        assertThatThrownBy(() -> client.readPassword("../etc"))
                .isInstanceOf(VaultSecretNotFoundException.class);
    }

    @Test
    void readPassword_staticToken_returnsKvV2Password() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://vault.example/v1/secrets/kv_v2/wsit/data/workflow/email/qq"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Vault-Token", "dev-token"))
                .andExpect(header("X-Vault-Namespace", "ITID/HERMES"))
                .andRespond(withSuccess(KV_BODY, MediaType.APPLICATION_JSON));

        VaultSecretClientImpl client = new VaultSecretClientImpl(restTemplate, staticTokenSettings());
        assertThat(client.readPassword("workflow/email/qq")).isEqualTo("auth-code");
        server.verify();
    }

    @Test
    void readPassword_404_throwsNotFound() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://vault.example/v1/secrets/kv_v2/wsit/data/missing"))
                .andRespond(withResourceNotFound());

        VaultSecretClientImpl client = new VaultSecretClientImpl(restTemplate, staticTokenSettings());
        assertThatThrownBy(() -> client.readPassword("missing"))
                .isInstanceOf(VaultSecretNotFoundException.class);
    }

    @Test
    void readPassword_kubernetesLogin_thenReadsSecret(@TempDir Path tempDir) throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "k8s-jwt");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://vault.example/v1/auth/kubernetes/wsit-hk-azx-401/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("X-Vault-Token"))
                .andRespond(withSuccess(
                        "{\"auth\":{\"client_token\":\"s.abc\",\"lease_duration\":3600}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://vault.example/v1/secrets/kv_v2/wsit/data/workflow/email/qq"))
                .andExpect(header("X-Vault-Token", "s.abc"))
                .andRespond(withSuccess(KV_BODY, MediaType.APPLICATION_JSON));

        VaultClientSettings settings = new VaultClientSettings(
                "http://vault.example",
                "ITID/HERMES",
                "kubernetes/wsit-hk-azx-401",
                "ame-hase-hermes-role",
                "secrets/kv_v2/wsit",
                tokenFile.toString(),
                "",
                30);
        VaultSecretClientImpl client = new VaultSecretClientImpl(restTemplate, settings);
        assertThat(client.readPassword("workflow/email/qq")).isEqualTo("auth-code");
        server.verify();
    }

    @Test
    void secretExists_404_returnsFalse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://vault.example/v1/secrets/kv_v2/wsit/data/missing"))
                .andRespond(withResourceNotFound());

        VaultSecretClientImpl client = new VaultSecretClientImpl(restTemplate, staticTokenSettings());
        assertThat(client.secretExists("missing")).isFalse();
        server.verify();
    }

    @Test
    void secretExists_200_returnsTrue() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://vault.example/v1/secrets/kv_v2/wsit/data/workflow/email/qq"))
                .andRespond(withSuccess(KV_BODY, MediaType.APPLICATION_JSON));

        VaultSecretClientImpl client = new VaultSecretClientImpl(restTemplate, staticTokenSettings());
        assertThat(client.secretExists("workflow/email/qq")).isTrue();
        server.verify();
    }

    @Test
    void writePassword_postsKvV2Password() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://vault.example/v1/secrets/kv_v2/wsit/data/workflow/email/qq"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Vault-Token", "dev-token"))
                .andExpect(content().json("{\"data\":{\"password\":\"auth-code\"}}"))
                .andRespond(withSuccess("{\"data\":{\"version\":1}}", MediaType.APPLICATION_JSON));

        VaultSecretClientImpl client = new VaultSecretClientImpl(restTemplate, staticTokenSettings());
        client.writePassword("workflow/email/qq", "auth-code");
        server.verify();
    }

    private static ObjectProvider<RestTemplate> emptyRestTemplateProvider() {
        return new ObjectProvider<>() {
            @Override
            public RestTemplate getObject() throws BeansException {
                return null;
            }

            @Override
            public RestTemplate getObject(Object... args) throws BeansException {
                return null;
            }

            @Override
            public RestTemplate getIfAvailable() throws BeansException {
                return null;
            }

            @Override
            public RestTemplate getIfUnique() throws BeansException {
                return null;
            }
        };
    }

    private static VaultClientSettings staticTokenSettings() {
        return new VaultClientSettings(
                "http://vault.example",
                "ITID/HERMES",
                "kubernetes/wsit-hk-azx-401",
                "ame-hase-hermes-role",
                "secrets/kv_v2/wsit",
                "",
                "dev-token",
                30);
    }
}
