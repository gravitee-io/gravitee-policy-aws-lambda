/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.aws.lambda;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@GatewayTest
@DeployApi(
    {
        "/apis/aws-lambda-on-request.json",
        "/apis/aws-lambda-on-request-content.json",
        "/apis/aws-lambda-on-response.json",
        "/apis/aws-lambda-on-response-content.json",
    }
)
public class AwsLambdaPolicyV3CompatibilityIntegrationTest extends AbstractPolicyTest<AwsLambdaPolicy, AwsLambdaPolicyConfiguration> {

    @RegisterExtension
    static WireMockExtension awsLambdaMock = WireMockExtension.newInstance().options(wireMockConfig().port(8080)).build();

    @Override
    public void configurePolicies(Map<String, PolicyPlugin> policies) {
        policies.put("copy-aws-lambda-attribute", PolicyBuilder.build("copy-aws-lambda-attribute", CopyAwsLambdaAttributePolicy.class));
        policies.put(
            "aws-lambda-test-policy",
            PolicyBuilder.build("aws-lambda-test-policy", AwsLambdaTestPolicy.class, AwsLambdaPolicyConfiguration.class)
        );
    }

    @Override
    protected void configureGateway(GatewayConfigurationBuilder gatewayConfigurationBuilder) {
        super.configureGateway(gatewayConfigurationBuilder);
        gatewayConfigurationBuilder.set("api.jupiterMode.enabled", "true");
    }

    @Override
    public void configureApi(Api api) {
        super.configureApi(api);
        api.setExecutionMode(ExecutionMode.V3);
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    @DisplayName("Should use AWS Lambda policy and return error from AWS")
    void shouldUseAwsLambdaPolicyAndReturnError(String path, int endpointCallCount, HttpClient client) throws Exception {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));
        awsLambdaMock.stubFor(post(anyUrl()).willReturn(forbidden()));

        client
            .rxRequest(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertValue(error -> {
                assertThat(error.toString()).contains("An error occurs while invoking lambda function.");
                assertThat(error.toString()).contains("Status Code: 403");
                return true;
            });

        wiremock.verify(exactly(endpointCallCount), getRequestedFor(urlPathEqualTo("/endpoint")));
        awsLambdaMock.verify(postRequestedFor(urlMatching("/\\d{4}-\\d{2}-\\d{2}/functions/lambda-example/invocations")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/on-request", "/on-request-content", "/on-response", "/on-response-content" })
    @DisplayName("Should use AWS Lambda policy and set response as attribute")
    void shouldUseAwsLambdaPolicyAndSetResponseAsAttribute(String path, HttpClient client) throws InterruptedException {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));
        awsLambdaMock.stubFor(post(anyUrl()).willReturn(ok("response from lambda")));

        client
            .rxRequest(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertValue(body -> {
                assertThat(body.toString()).isEqualTo("response from lambda");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(getRequestedFor(urlPathEqualTo("/endpoint")));
        awsLambdaMock.verify(postRequestedFor(urlMatching("/\\d{4}-\\d{2}-\\d{2}/functions/lambda-example/invocations")));
    }

    private static Stream<Arguments> provideParameters() {
        return Stream.of(
            Arguments.of("/on-request", 0),
            Arguments.of("/on-request-content", 0),
            Arguments.of("/on-response", 1),
            Arguments.of("/on-response-content", 1)
        );
    }
}
