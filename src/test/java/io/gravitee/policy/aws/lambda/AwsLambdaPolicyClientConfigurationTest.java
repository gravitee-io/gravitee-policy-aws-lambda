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

import com.amazonaws.ClientConfiguration;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import org.junit.jupiter.api.Test;

class AwsLambdaPolicyClientConfigurationTest {

    private AwsLambdaPolicyConfiguration configuration = new AwsLambdaPolicyConfiguration();

    private AwsLambdaPolicy createPolicy() {
        return new AwsLambdaTestPolicy(createTestConfiguration());
    }

    private AwsLambdaTestPolicyConfiguration createTestConfiguration() {
        AwsLambdaTestPolicyConfiguration testConfig = new AwsLambdaTestPolicyConfiguration();
        testConfig.setRegion(configuration.getRegion());
        testConfig.setAccessKey("test-key");
        testConfig.setSecretKey("test-secret");
        testConfig.setFunction("test-function");
        testConfig.setEndpoint("http://localhost:9999");
        testConfig.setConnectionTimeoutMs(configuration.getConnectionTimeoutMs());
        testConfig.setSocketTimeoutMs(configuration.getSocketTimeoutMs());
        testConfig.setRequestTimeoutMs(configuration.getRequestTimeoutMs());
        testConfig.setClientExecutionTimeoutMs(configuration.getClientExecutionTimeoutMs());
        return testConfig;
    }

    @Test
    void shouldReturnNullClientConfigurationWhenNoTimeoutsSet() {
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNull();
    }

    @Test
    void shouldSetConnectionTimeout() {
        configuration.setConnectionTimeoutMs(5000);
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNotNull();
        assertThat(clientConfig.getConnectionTimeout()).isEqualTo(5000);
    }

    @Test
    void shouldSetSocketTimeout() {
        configuration.setSocketTimeoutMs(30000);
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNotNull();
        assertThat(clientConfig.getSocketTimeout()).isEqualTo(30000);
    }

    @Test
    void shouldSetRequestTimeout() {
        configuration.setRequestTimeoutMs(15000);
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNotNull();
        assertThat(clientConfig.getRequestTimeout()).isEqualTo(15000);
    }

    @Test
    void shouldSetClientExecutionTimeout() {
        configuration.setClientExecutionTimeoutMs(60000);
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNotNull();
        assertThat(clientConfig.getClientExecutionTimeout()).isEqualTo(60000);
    }

    @Test
    void shouldSetAllTimeouts() {
        configuration.setConnectionTimeoutMs(5000);
        configuration.setSocketTimeoutMs(30000);
        configuration.setRequestTimeoutMs(15000);
        configuration.setClientExecutionTimeoutMs(60000);
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNotNull();
        assertThat(clientConfig.getConnectionTimeout()).isEqualTo(5000);
        assertThat(clientConfig.getSocketTimeout()).isEqualTo(30000);
        assertThat(clientConfig.getRequestTimeout()).isEqualTo(15000);
        assertThat(clientConfig.getClientExecutionTimeout()).isEqualTo(60000);
    }

    @Test
    void shouldOnlySetConfiguredTimeoutsAndLeaveOthersAsDefault() {
        configuration.setConnectionTimeoutMs(5000);
        configuration.setClientExecutionTimeoutMs(60000);
        AwsLambdaPolicy policy = createPolicy();

        ClientConfiguration clientConfig = policy.buildClientConfiguration();

        assertThat(clientConfig).isNotNull();
        assertThat(clientConfig.getConnectionTimeout()).isEqualTo(5000);
        assertThat(clientConfig.getSocketTimeout()).isEqualTo(ClientConfiguration.DEFAULT_SOCKET_TIMEOUT);
        assertThat(clientConfig.getRequestTimeout()).isEqualTo(ClientConfiguration.DEFAULT_REQUEST_TIMEOUT);
        assertThat(clientConfig.getClientExecutionTimeout()).isEqualTo(60000);
    }
}
