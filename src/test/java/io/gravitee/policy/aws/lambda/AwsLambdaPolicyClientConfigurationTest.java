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

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

class AwsLambdaPolicyClientConfigurationTest {

    private AwsLambdaTestPolicyConfiguration createDefaultConfig() {
        var config = new AwsLambdaTestPolicyConfiguration();
        config.setAccessKey("test-key");
        config.setSecretKey("test-secret");
        config.setFunction("test-function");
        config.setEndpoint("http://localhost:9999");
        return config;
    }

    // --- buildClientOverrideConfiguration tests ---

    @Test
    void shouldReturnEmptyOverrideConfigurationWhenNoTimeoutsSet() {
        var config = createDefaultConfig();
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        ClientOverrideConfiguration result = policy.buildClientOverrideConfiguration(config);

        assertThat(result.apiCallTimeout()).isEmpty();
        assertThat(result.apiCallAttemptTimeout()).isEmpty();
    }

    @Test
    void shouldSetApiCallTimeout() {
        var config = createDefaultConfig();
        config.setApiCallTimeoutMs(60000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        ClientOverrideConfiguration overrideConfig = policy.buildClientOverrideConfiguration(config);

        assertThat(overrideConfig).isNotNull();
        assertThat(overrideConfig.apiCallTimeout()).contains(Duration.ofMillis(60000));
        assertThat(overrideConfig.apiCallAttemptTimeout()).isEmpty();
    }

    @Test
    void shouldSetApiCallAttemptTimeout() {
        var config = createDefaultConfig();
        config.setApiCallAttemptTimeoutMs(15000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        ClientOverrideConfiguration overrideConfig = policy.buildClientOverrideConfiguration(config);

        assertThat(overrideConfig).isNotNull();
        assertThat(overrideConfig.apiCallAttemptTimeout()).contains(Duration.ofMillis(15000));
        assertThat(overrideConfig.apiCallTimeout()).isEmpty();
    }

    @Test
    void shouldSetBothApiCallTimeouts() {
        var config = createDefaultConfig();
        config.setApiCallTimeoutMs(60000);
        config.setApiCallAttemptTimeoutMs(15000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        ClientOverrideConfiguration overrideConfig = policy.buildClientOverrideConfiguration(config);

        assertThat(overrideConfig).isNotNull();
        assertThat(overrideConfig.apiCallTimeout()).contains(Duration.ofMillis(60000));
        assertThat(overrideConfig.apiCallAttemptTimeout()).contains(Duration.ofMillis(15000));
    }

    @Test
    void shouldReturnEmptyOverrideConfigurationWhenOnlyHttpTimeoutsSet() {
        var config = createDefaultConfig();
        config.setConnectionTimeoutMs(5000);
        config.setReadTimeoutMs(30000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        ClientOverrideConfiguration result = policy.buildClientOverrideConfiguration(config);

        assertThat(result.apiCallTimeout()).isEmpty();
        assertThat(result.apiCallAttemptTimeout()).isEmpty();
    }

    // --- buildHttpClient tests ---

    @Test
    void shouldSetConnectionTimeout() {
        var config = createDefaultConfig();
        config.setConnectionTimeoutMs(5000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        NettyNioAsyncHttpClient.Builder httpClientBuilder = policy.buildHttpClient(config, null);

        try (var httpClient = httpClientBuilder.build()) {
            assertThat(httpClient).isNotNull();
        }
    }

    @Test
    void shouldSetReadTimeout() {
        var config = createDefaultConfig();
        config.setReadTimeoutMs(30000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        NettyNioAsyncHttpClient.Builder httpClientBuilder = policy.buildHttpClient(config, null);

        try (var httpClient = httpClientBuilder.build()) {
            assertThat(httpClient).isNotNull();
        }
    }

    @Test
    void shouldSetBothHttpTimeouts() {
        var config = createDefaultConfig();
        config.setConnectionTimeoutMs(5000);
        config.setReadTimeoutMs(30000);
        AwsLambdaPolicyV3 policy = new AwsLambdaTestPolicy(config);

        NettyNioAsyncHttpClient.Builder httpClientBuilder = policy.buildHttpClient(config, null);

        try (var httpClient = httpClientBuilder.build()) {
            assertThat(httpClient).isNotNull();
        }
    }
}
