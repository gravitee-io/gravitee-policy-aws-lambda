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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/** Regression tests for gravitee-io/issues#11552 (event-loop blocking on V4 Lambda invocation). */
class AwsLambdaPolicyNonBlockingTest {

    private static final long LAMBDA_LATENCY_MS = 3_000L;
    private static final long NON_BLOCKING_BUDGET_MS = 1_500L;

    private ScheduledExecutorService lambdaExecutor;
    private LambdaAsyncClient lambdaClient;

    @BeforeEach
    void setUp() {
        AwsLambdaClientCache.invalidateAll();
        lambdaExecutor = Executors.newSingleThreadScheduledExecutor();
        lambdaClient = mock(LambdaAsyncClient.class);
    }

    @AfterEach
    void tearDown() {
        lambdaExecutor.shutdownNow();
        AwsLambdaClientCache.invalidateAll();
    }

    private AwsLambdaTestPolicyConfiguration baseConfig() {
        var config = new AwsLambdaTestPolicyConfiguration();
        config.setAccessKey("test-key");
        config.setSecretKey("test-secret");
        config.setFunction("test-function");
        config.setEndpoint("http://localhost:9999");
        return config;
    }

    private AwsLambdaPolicyV3 policyWithMockClient(AwsLambdaTestPolicyConfiguration config) {
        return new AwsLambdaTestPolicy(config) {
            @Override
            protected LambdaAsyncClient initLambdaClient(AwsLambdaPolicyConfiguration cfg) {
                return lambdaClient;
            }
        };
    }

    private CompletableFuture<InvokeResponse> delayedInvokeResponse(long delayMs) {
        var future = new CompletableFuture<InvokeResponse>();
        lambdaExecutor.schedule(
            () -> future.complete(InvokeResponse.builder().statusCode(200).payload(SdkBytes.fromUtf8String("{\"ok\":true}")).build()),
            delayMs,
            TimeUnit.MILLISECONDS
        );
        return future;
    }

    private CompletableFuture<InvokeResponse> failedInvokeFuture(Throwable cause) {
        var failed = new CompletableFuture<InvokeResponse>();
        failed.completeExceptionally(cause);
        return failed;
    }

    @Test
    @DisplayName("invokeLambdaReactive must not block the subscribing thread")
    void reactiveOverloadDoesNotBlockSubscribingThread() {
        AwsLambdaTestPolicyConfiguration config = baseConfig();
        AwsLambdaPolicyV3 policy = policyWithMockClient(config);
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(delayedInvokeResponse(LAMBDA_LATENCY_MS));

        long start = System.nanoTime();
        Single<InvokeResponse> single = policy.invokeLambdaReactive(Single.just(config));
        TestObserver<InvokeResponse> observer = single.test();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs)
            .as("Subscription must return immediately — bug would block for %d ms", LAMBDA_LATENCY_MS)
            .isLessThan(NON_BLOCKING_BUDGET_MS);
        assertThat(observer.values()).as("Response must not have arrived yet — the future is still pending").isEmpty();

        observer.awaitDone(LAMBDA_LATENCY_MS + 2_000L, TimeUnit.MILLISECONDS).assertComplete().assertValueCount(1);
        assertThat(observer.values().get(0).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Legacy CompletableFuture-returning invokeLambda must not block the caller")
    void legacyCompletableFutureOverloadDoesNotBlockCaller() throws Exception {
        AwsLambdaTestPolicyConfiguration config = baseConfig();
        AwsLambdaPolicyV3 policy = policyWithMockClient(config);
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(delayedInvokeResponse(LAMBDA_LATENCY_MS));

        long start = System.nanoTime();
        CompletableFuture<InvokeResponse> future = policy.invokeLambda(Single.just(config));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs)
            .as("invokeLambda(Single) must return the future immediately, not block for %d ms", LAMBDA_LATENCY_MS)
            .isLessThan(NON_BLOCKING_BUDGET_MS);
        assertThat(future).isNotCompleted();

        InvokeResponse response = future.get(LAMBDA_LATENCY_MS + 2_000L, TimeUnit.MILLISECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Reactive overload does not eagerly invoke the AWS SDK before subscription")
    void reactiveOverloadIsLazy() {
        AwsLambdaTestPolicyConfiguration config = baseConfig();
        AwsLambdaPolicyV3 policy = policyWithMockClient(config);
        AtomicBoolean invoked = new AtomicBoolean(false);
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenAnswer(inv -> {
            invoked.set(true);
            return CompletableFuture.completedFuture(
                InvokeResponse.builder().statusCode(200).payload(SdkBytes.fromUtf8String("{}")).build()
            );
        });

        Single<InvokeResponse> single = policy.invokeLambdaReactive(Single.just(config));

        assertThat(invoked).as("Single must be cold — no AWS call before subscribe").isFalse();

        single.test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(invoked).as("AWS call must have been triggered by the subscribe").isTrue();
    }

    @Test
    @DisplayName("Reactive overload propagates AWS SDK errors through onError")
    void reactiveOverloadPropagatesErrors() {
        AwsLambdaTestPolicyConfiguration config = baseConfig();
        AwsLambdaPolicyV3 policy = policyWithMockClient(config);
        var cause = new RuntimeException("boom");
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(failedInvokeFuture(cause));

        TestObserver<InvokeResponse> observer = policy.invokeLambdaReactive(Single.just(config)).test().awaitDone(1, TimeUnit.SECONDS);

        observer
            .assertNotComplete()
            .assertError(error -> {
                if (error instanceof CompletionException) {
                    return cause.equals(error.getCause());
                }
                return cause.equals(error);
            });
    }

    @Test
    @DisplayName("Legacy CompletableFuture overload propagates AWS SDK errors")
    void legacyOverloadPropagatesErrors() {
        AwsLambdaTestPolicyConfiguration config = baseConfig();
        AwsLambdaPolicyV3 policy = policyWithMockClient(config);
        var cause = new RuntimeException("boom");
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(failedInvokeFuture(cause));

        CompletableFuture<InvokeResponse> future = policy.invokeLambda(Single.just(config));

        assertThat(future).failsWithin(1, TimeUnit.SECONDS).withThrowableThat().withRootCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Payload is forwarded to AWS SDK when set on the configuration")
    void payloadIsForwardedToAwsSdk() {
        AwsLambdaTestPolicyConfiguration config = baseConfig();
        config.setPayload("{\"hello\":\"world\"}");
        AwsLambdaPolicyV3 policy = policyWithMockClient(config);

        var captured = new java.util.concurrent.atomic.AtomicReference<InvokeRequest>();
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return CompletableFuture.completedFuture(
                InvokeResponse.builder().statusCode(200).payload(SdkBytes.fromUtf8String("{}")).build()
            );
        });

        policy.invokeLambdaReactive(Single.just(config)).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().functionName()).isEqualTo("test-function");
        assertThat(captured.get().payload().asUtf8String()).isEqualTo("{\"hello\":\"world\"}");
    }
}
