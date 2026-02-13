/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.aws.lambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gravitee.policy.aws.lambda.configuration.SslConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.TlsTrustManagersProvider;

class AwsLambdaSslHelperTest {

    @Test
    void should_return_null_when_ssl_is_null() {
        assertThat(AwsLambdaSslHelper.buildTrustManagersProvider(null)).isNull();
    }

    @Test
    void should_return_null_when_ssl_has_no_type_and_not_trust_all() {
        SslConfiguration ssl = new SslConfiguration();
        assertThat(AwsLambdaSslHelper.buildTrustManagersProvider(ssl)).isNull();
    }

    @Test
    void should_return_null_when_type_is_empty() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("");
        assertThat(AwsLambdaSslHelper.buildTrustManagersProvider(ssl)).isNull();
    }

    @Test
    void should_return_trust_all_provider() throws Exception {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustAll(true);

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        TrustManager[] managers = provider.trustManagers();
        assertThat(managers).hasSize(1);
        assertThat(managers[0]).isInstanceOf(X509TrustManager.class);

        X509TrustManager tm = (X509TrustManager) managers[0];
        assertThat(tm.getAcceptedIssuers()).isEmpty();
        // Should not throw - trusts everything
        tm.checkServerTrusted(null, null);
        tm.checkClientTrusted(null, null);
    }

    @Test
    void should_build_provider_from_pem_content() throws Exception {
        String pem = readResource("keys/test1.pem");

        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("PEM");
        ssl.setTrustStoreContent(pem);

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        TrustManager[] managers = provider.trustManagers();
        assertThat(managers).hasSize(1);
        assertThat(managers[0]).isInstanceOf(X509TrustManager.class);

        X509TrustManager tm = (X509TrustManager) managers[0];
        assertThat(tm.getAcceptedIssuers()).hasSize(1);
    }

    @Test
    void should_build_provider_from_pem_content_with_multiple_certs() throws Exception {
        String pem = readResource("keys/test-both.pem");

        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("PEM");
        ssl.setTrustStoreContent(pem);

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        X509TrustManager tm = (X509TrustManager) provider.trustManagers()[0];
        assertThat(tm.getAcceptedIssuers()).hasSize(2);
    }

    @Test
    void should_build_provider_from_pem_file() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("PEM");
        ssl.setTrustStorePath(resourcePath("keys/test1.pem"));

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        X509TrustManager tm = (X509TrustManager) provider.trustManagers()[0];
        assertThat(tm.getAcceptedIssuers()).hasSize(1);
    }

    @Test
    void should_return_null_for_pem_with_no_content_and_no_path() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("PEM");

        assertThat(AwsLambdaSslHelper.buildTrustManagersProvider(ssl)).isNull();
    }

    @Test
    void should_build_provider_from_jks_file() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("JKS");
        ssl.setTrustStorePath(resourcePath("keys/test-truststore.jks"));
        ssl.setTrustStorePassword("changeit");

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        X509TrustManager tm = (X509TrustManager) provider.trustManagers()[0];
        assertThat(tm.getAcceptedIssuers()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void should_build_provider_from_pkcs12_file() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("PKCS12");
        ssl.setTrustStorePath(resourcePath("keys/test-truststore.p12"));
        ssl.setTrustStorePassword("secret");

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        X509TrustManager tm = (X509TrustManager) provider.trustManagers()[0];
        assertThat(tm.getAcceptedIssuers()).hasSize(1);
    }

    @Test
    void should_return_null_for_keystore_with_no_path() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("JKS");

        assertThat(AwsLambdaSslHelper.buildTrustManagersProvider(ssl)).isNull();
    }

    @Test
    void should_throw_for_invalid_keystore_path() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("JKS");
        ssl.setTrustStorePath("/nonexistent/path/truststore.jks");

        assertThatThrownBy(() -> AwsLambdaSslHelper.buildTrustManagersProvider(ssl))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to configure SSL truststore");
    }

    @Test
    void should_prefer_pem_content_over_path() throws Exception {
        // Content has two certs
        String pemContent = readResource("keys/test-both.pem");

        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustStoreType("PEM");
        ssl.setTrustStoreContent(pemContent);
        // Path has one cert — should be ignored in favor of content
        ssl.setTrustStorePath(resourcePath("keys/test1.pem"));

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        // Should use content (2 certs), not path (1 cert)
        X509TrustManager tm = (X509TrustManager) provider.trustManagers()[0];
        assertThat(tm.getAcceptedIssuers()).hasSize(2);
    }

    @Test
    void should_prioritize_trust_all_over_truststore() {
        SslConfiguration ssl = new SslConfiguration();
        ssl.setTrustAll(true);
        ssl.setTrustStoreType("JKS");
        ssl.setTrustStorePath("/some/path.jks");

        TlsTrustManagersProvider provider = AwsLambdaSslHelper.buildTrustManagersProvider(ssl);

        assertThat(provider).isNotNull();
        X509TrustManager tm = (X509TrustManager) provider.trustManagers()[0];
        assertThat(tm.getAcceptedIssuers()).isEmpty();
    }

    // --- Helpers ---

    private static String resourcePath(String resource) {
        return Objects.requireNonNull(AwsLambdaSslHelperTest.class.getClassLoader().getResource(resource)).getPath();
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream is = Objects.requireNonNull(AwsLambdaSslHelperTest.class.getClassLoader().getResourceAsStream(resource))) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
