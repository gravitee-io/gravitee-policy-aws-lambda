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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * Verifies STS client lifecycle: AWS SDK does not close a caller-supplied {@link StsClient},
 * and {@link OwnedStsCredentialsProvider} closes it when the Lambda client / cache evicts.
 */
class OwnedStsCredentialsProviderTest {

    @BeforeEach
    @AfterEach
    void clearCache() {
        AwsLambdaClientCache.invalidateAll();
    }

    @Test
    void close_stopsCredentialRefreshThenClosesStsClient() {
        StsAssumeRoleCredentialsProvider credentialsProvider = mock(StsAssumeRoleCredentialsProvider.class);
        StsClient stsClient = mock(StsClient.class);

        new OwnedStsCredentialsProvider(credentialsProvider, stsClient).close();

        InOrder order = inOrder(credentialsProvider, stsClient);
        order.verify(credentialsProvider).close();
        order.verify(stsClient).close();
    }

    @Test
    void close_isIdempotent() {
        StsAssumeRoleCredentialsProvider credentialsProvider = mock(StsAssumeRoleCredentialsProvider.class);
        StsClient stsClient = mock(StsClient.class);
        OwnedStsCredentialsProvider owned = new OwnedStsCredentialsProvider(credentialsProvider, stsClient);

        owned.close();
        owned.close();

        verify(credentialsProvider).close();
        verify(stsClient).close();
    }

    @Test
    void close_stillClosesStsClientWhenCredentialsProviderCloseFails() {
        StsAssumeRoleCredentialsProvider credentialsProvider = mock(StsAssumeRoleCredentialsProvider.class);
        StsClient stsClient = mock(StsClient.class);
        doThrow(new RuntimeException("provider close failed")).when(credentialsProvider).close();

        try {
            new OwnedStsCredentialsProvider(credentialsProvider, stsClient).close();
        } catch (RuntimeException ignored) {
            // expected from credentials provider
        }

        verify(stsClient).close();
    }

    @Test
    @DisplayName("SDK gap: LambdaAsyncClient.close() does not close a caller-supplied StsClient")
    void lambdaClientClose_withoutWrapper_doesNotCloseStsClient() {
        TrackingStsClient stsClient = new TrackingStsClient();
        StsAssumeRoleCredentialsProvider provider = assumeRoleProvider(stsClient);

        LambdaAsyncClient lambda = LambdaAsyncClient.builder()
            .credentialsProvider(provider)
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create("http://127.0.0.1:9"))
            .build();

        lambda.close();

        assertThat(stsClient.closeCount.get()).as("AWS SDK leaves caller-supplied StsClient open — the leak we wrap").isZero();
        provider.close();
        stsClient.close();
    }

    @Test
    @DisplayName("Owned wrapper: LambdaAsyncClient.close() closes StsClient (same path as cache eviction)")
    void lambdaClientClose_withOwnedWrapper_closesStsClient() {
        TrackingStsClient stsClient = new TrackingStsClient();
        StsAssumeRoleCredentialsProvider provider = assumeRoleProvider(stsClient);
        OwnedStsCredentialsProvider owned = new OwnedStsCredentialsProvider(provider, stsClient);

        LambdaAsyncClient lambda = LambdaAsyncClient.builder()
            .credentialsProvider(owned)
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create("http://127.0.0.1:9"))
            .build();

        lambda.close();

        assertThat(stsClient.closed.get()).as("AttributeMap must close OwnedStsCredentialsProvider which closes StsClient").isTrue();
        assertThat(stsClient.closeCount.get()).as("close must be idempotent even if AttributeMap visits credentials twice").isEqualTo(1);
    }

    @Test
    @DisplayName("Cache invalidateAll closes StsClient for roleArn clients")
    void cacheInvalidation_closesOwnedStsClient() {
        TrackingStsClient stsClient = new TrackingStsClient();
        StsAssumeRoleCredentialsProvider provider = assumeRoleProvider(stsClient);
        OwnedStsCredentialsProvider owned = new OwnedStsCredentialsProvider(provider, stsClient);

        LambdaAsyncClient lambda = LambdaAsyncClient.builder()
            .credentialsProvider(owned)
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create("http://127.0.0.1:9"))
            .build();

        AwsLambdaTestPolicyConfiguration config = new AwsLambdaTestPolicyConfiguration();
        config.setAccessKey("AKIA_TEST");
        config.setSecretKey("secret");
        config.setRegion("us-east-1");
        config.setRoleArn("arn:aws:iam::000000000000:role/test");
        config.setRoleSessionName("gravitee");
        config.setFunction("fn");
        config.setEndpoint("http://127.0.0.1:9");

        LambdaAsyncClient cached = AwsLambdaClientCache.get(config, ignored -> lambda);
        assertThat(cached).isSameAs(lambda);
        assertThat(stsClient.closeCount.get()).isZero();

        AwsLambdaClientCache.invalidateAll();

        // Caffeine removalListener runs asynchronously on the common pool
        awaitUntil(stsClient.closed::get, 5, TimeUnit.SECONDS);
        assertThat(stsClient.closeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Policy initLambdaClient with roleArn closes cleanly via cache invalidation")
    void policyInit_withRoleArn_closesCleanlyOnCacheInvalidation() {
        AwsLambdaTestPolicyConfiguration config = new AwsLambdaTestPolicyConfiguration();
        config.setAccessKey("AKIA_TEST");
        config.setSecretKey("secret");
        config.setRegion("us-east-1");
        config.setRoleArn("arn:aws:iam::000000000000:role/test");
        config.setRoleSessionName("gravitee");
        config.setFunction("fn");
        config.setEndpoint("http://127.0.0.1:9");
        config.setConnectionTimeoutMs(100);
        config.setReadTimeoutMs(100);

        AwsLambdaPolicyV3 policy = new AwsLambdaPolicyV3(config);
        LambdaAsyncClient first = AwsLambdaClientCache.get(config, policy::initLambdaClient);
        assertThat(first).isNotNull();

        AwsLambdaClientCache.invalidateAll();
        awaitUntil(
            () -> {
                LambdaAsyncClient second = AwsLambdaClientCache.get(config, policy::initLambdaClient);
                boolean ok = second != null && second != first;
                if (ok) {
                    AwsLambdaClientCache.invalidateAll();
                }
                return ok;
            },
            5,
            TimeUnit.SECONDS
        );
    }

    private static StsAssumeRoleCredentialsProvider assumeRoleProvider(StsClient stsClient) {
        return StsAssumeRoleCredentialsProvider.builder()
            .stsClient(stsClient)
            .refreshRequest(() ->
                AssumeRoleRequest.builder().roleArn("arn:aws:iam::000000000000:role/test").roleSessionName("gravitee").build()
            )
            .asyncCredentialUpdateEnabled(true)
            .build();
    }

    private static void awaitUntil(BooleanSupplier condition, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within " + timeout + " " + unit);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", e);
            }
        }
    }

    /** Minimal StsClient that records close() without needing Mockito stubbing of AWS internals. */
    private static final class TrackingStsClient implements StsClient {

        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public String serviceName() {
            return SERVICE_NAME;
        }

        @Override
        public void close() {
            closed.set(true);
            closeCount.incrementAndGet();
        }
    }
}
