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

import io.gravitee.policy.aws.lambda.configuration.SslConfiguration;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.http.TlsTrustManagersProvider;

@Slf4j
public final class AwsLambdaSslHelper {

    private static final String ALIAS = "cert-";

    private static final TrustManager[] TRUST_ALL_MANAGERS = {
        new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        },
    };

    private AwsLambdaSslHelper() {}

    public static TlsTrustManagersProvider buildTrustManagersProvider(SslConfiguration ssl) {
        if (ssl == null) {
            return null;
        }

        if (ssl.isTrustAll()) {
            log.warn("AWS Lambda policy SSL configured with trustAll=true; certificate validation is disabled");
            return () -> TRUST_ALL_MANAGERS;
        }

        String type = ssl.getTrustStoreType();
        if (type == null || type.isEmpty()) {
            return null;
        }

        try {
            if ("PEM".equalsIgnoreCase(type)) {
                return buildPemProvider(ssl);
            } else {
                return buildKeystoreProvider(ssl, type);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure SSL truststore for AWS Lambda policy", e);
        }
    }

    private static TlsTrustManagersProvider buildPemProvider(SslConfiguration ssl) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs = getCertificates(ssl, cf);

        if (certs == null || certs.isEmpty()) return null;

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        int i = 0;
        for (Certificate cert : certs) {
            ks.setCertificateEntry(ALIAS + i++, cert);
        }

        return toProvider(ks);
    }

    private static Collection<? extends Certificate> getCertificates(SslConfiguration ssl, CertificateFactory cf)
        throws IOException, CertificateException {
        String trustStoreContent = ssl.getTrustStoreContent();
        String trustStorePath = ssl.getTrustStorePath();

        if (trustStoreContent != null && !trustStoreContent.isEmpty()) {
            try (InputStream is = new ByteArrayInputStream(trustStoreContent.getBytes(StandardCharsets.UTF_8))) {
                return cf.generateCertificates(is);
            }
        } else if (trustStorePath != null && !trustStorePath.isEmpty()) {
            try (InputStream is = new FileInputStream(trustStorePath)) {
                return cf.generateCertificates(is);
            }
        }

        return null;
    }

    private static TlsTrustManagersProvider buildKeystoreProvider(SslConfiguration ssl, String type) throws Exception {
        if (ssl.getTrustStorePath() == null || ssl.getTrustStorePath().isEmpty()) {
            return null;
        }

        KeyStore ks = loadKeystore(ssl, type);
        return toProvider(ks);
    }

    private static TlsTrustManagersProvider toProvider(KeyStore ks) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        tmf.init(ks);
        TrustManager[] managers = tmf.getTrustManagers();

        return () -> managers;
    }

    private static @NonNull KeyStore loadKeystore(SslConfiguration ssl, String type)
        throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore ks = KeyStore.getInstance(type);
        char[] password = ssl.getTrustStorePassword() != null ? ssl.getTrustStorePassword().toCharArray() : null;

        try (InputStream is = new FileInputStream(ssl.getTrustStorePath())) {
            ks.load(is, password);
        }

        return ks;
    }
}
