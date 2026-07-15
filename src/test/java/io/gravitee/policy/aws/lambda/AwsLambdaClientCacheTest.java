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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.reactivex.rxjava3.core.Single;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

class AwsLambdaClientCacheTest {

    @org.junit.jupiter.api.BeforeEach
    void clearCache() {
        AwsLambdaClientCache.invalidateAll();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownCache() {
        AwsLambdaClientCache.invalidateAll();
    }

    private AwsLambdaTestPolicyConfiguration baseConfig() {
        var config = new AwsLambdaTestPolicyConfiguration();
        config.setAccessKey("test-key");
        config.setSecretKey("test-secret");
        config.setFunction("test-function");
        config.setRegion("us-east-1");
        config.setEndpoint("http://localhost:9999");
        return config;
    }

    @Test
    void shouldReuseClientForSameResolvedConfiguration() {
        var config = baseConfig();
        AtomicInteger creations = new AtomicInteger();
        LambdaAsyncClient client = mock(LambdaAsyncClient.class);
        when(client.invoke(org.mockito.ArgumentMatchers.any(InvokeRequest.class))).thenReturn(
            CompletableFuture.completedFuture(InvokeResponse.builder().statusCode(200).build())
        );

        AwsLambdaPolicyV3 policy = new AwsLambdaPolicyV3(config) {
            @Override
            protected LambdaAsyncClient initLambdaClient(AwsLambdaPolicyConfiguration cfg) {
                creations.incrementAndGet();
                return client;
            }
        };

        policy.invokeLambdaReactive(Single.just(config)).test().assertComplete().assertValueCount(1);
        policy.invokeLambdaReactive(Single.just(config)).test().assertComplete().assertValueCount(1);

        assertThat(creations).hasValue(1);
    }

    @Test
    void shouldCreateDistinctClientsForDifferentCredentials() {
        var configA = baseConfig();
        var configB = baseConfig();
        configB.setSecretKey("other-secret");

        AtomicInteger creations = new AtomicInteger();
        LambdaAsyncClient client = mock(LambdaAsyncClient.class);
        when(client.invoke(org.mockito.ArgumentMatchers.any(InvokeRequest.class))).thenReturn(
            CompletableFuture.completedFuture(InvokeResponse.builder().statusCode(200).build())
        );

        AwsLambdaPolicyV3 policy = new AwsLambdaPolicyV3(configA) {
            @Override
            protected LambdaAsyncClient initLambdaClient(AwsLambdaPolicyConfiguration cfg) {
                creations.incrementAndGet();
                return client;
            }
        };

        policy.invokeLambdaReactive(Single.just(configA)).test().assertComplete();
        policy.invokeLambdaReactive(Single.just(configB)).test().assertComplete();

        assertThat(creations).hasValue(2);
    }

    @Test
    void shouldTreatSslConfigurationAsPartOfCacheKey() {
        var configA = baseConfig();
        var configB = baseConfig();
        var ssl = new io.gravitee.policy.aws.lambda.configuration.SslConfiguration();
        ssl.setTrustAll(true);
        configB.setSsl(ssl);

        AtomicInteger creations = new AtomicInteger();
        LambdaAsyncClient client = mock(LambdaAsyncClient.class);
        when(client.invoke(org.mockito.ArgumentMatchers.any(InvokeRequest.class))).thenReturn(
            CompletableFuture.completedFuture(InvokeResponse.builder().statusCode(200).build())
        );

        AwsLambdaPolicyV3 policy = new AwsLambdaPolicyV3(configA) {
            @Override
            protected LambdaAsyncClient initLambdaClient(AwsLambdaPolicyConfiguration cfg) {
                creations.incrementAndGet();
                return client;
            }
        };

        policy.invokeLambdaReactive(Single.just(configA)).test().assertComplete();
        policy.invokeLambdaReactive(Single.just(configB)).test().assertComplete();

        assertThat(creations).hasValue(2);
    }

    @Test
    void testPolicyBypassesCacheForCustomEndpoint() {
        var config = baseConfig();
        AtomicInteger creations = new AtomicInteger();
        LambdaAsyncClient client = mock(LambdaAsyncClient.class);
        when(client.invoke(org.mockito.ArgumentMatchers.any(InvokeRequest.class))).thenReturn(
            CompletableFuture.completedFuture(InvokeResponse.builder().statusCode(200).build())
        );

        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config) {
            @Override
            protected LambdaAsyncClient initLambdaClient(AwsLambdaPolicyConfiguration cfg) {
                creations.incrementAndGet();
                return client;
            }
        };

        policy.invokeLambdaReactive(Single.just(config)).test().assertComplete();
        policy.invokeLambdaReactive(Single.just(config)).test().assertComplete();

        assertThat(creations).hasValue(2);
    }
}
