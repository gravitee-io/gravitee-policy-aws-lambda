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

import com.github.tomakehurst.wiremock.WireMockServer;
import io.gravitee.apim.gateway.tests.sdk.AbstractGatewayTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.reactor.ReactableApi;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@GatewayTest
@DeployApi(
    {
        "/apis/aws-lambda-on-request.json",
        "/apis/aws-lambda-on-request-content.json",
        "/apis/aws-lambda-on-response.json",
        "/apis/aws-lambda-on-response-content.json",
        "/apis/aws-lambda-with-send-to-consumer.json",
    }
)
public class AwsLambdaPolicyIntegrationTest extends AbstractGatewayTest {

    private WireMockServer awsLambdaMock;

    public AwsLambdaPolicyIntegrationTest() {
        awsLambdaMock = new WireMockServer(wireMockConfig().dynamicPort());
        awsLambdaMock.start();
    }

    @AfterAll
    public void tearDown() {
        if (null != awsLambdaMock) {
            awsLambdaMock.stop();
        }
    }

    @Override
    public void configurePolicies(Map<String, PolicyPlugin> policies) {
        policies.put("copy-aws-lambda-attribute", PolicyBuilder.build("copy-aws-lambda-attribute", CopyAwsLambdaAttributePolicy.class));
        policies.put(
            "aws-lambda-test-policy",
            PolicyBuilder.build("aws-lambda-test-policy", AwsLambdaTestPolicy.class, AwsLambdaTestPolicyConfiguration.class)
        );
    }

    @Override
    protected void configureGateway(GatewayConfigurationBuilder gatewayConfigurationBuilder) {
        super.configureGateway(gatewayConfigurationBuilder);
        gatewayConfigurationBuilder.set("api.jupiterMode.enabled", "true");
    }

    @Override
    public void configureApi(ReactableApi<?> api, Class<?> apiDefinitionClass) {
        if (apiDefinitionClass.isAssignableFrom(Api.class)) {
            ((Api) api.getDefinition()).setExecutionMode(ExecutionMode.JUPITER);
            ((Api) api.getDefinition()).getFlows()
                .forEach(flow -> {
                    flow
                        .getPre()
                        .stream()
                        .filter(policy -> "aws-lambda-test-policy".equals(policy.getPolicy()))
                        .findFirst()
                        .ifPresent(policy -> {
                            policy.setConfiguration(policy.getConfiguration().replace("http://localhost:9999", awsLambdaMock.baseUrl()));
                        });

                    flow
                        .getPost()
                        .stream()
                        .filter(policy -> "aws-lambda-test-policy".equals(policy.getPolicy()))
                        .findFirst()
                        .ifPresent(policy -> {
                            policy.setConfiguration(policy.getConfiguration().replace("http://localhost:9999", awsLambdaMock.baseUrl()));
                        });
                });
        }
    }

    @ParameterizedTest
    @MethodSource("providePath")
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

    /**
     * TODO: Here we skip the "/request-content" because the test is not stable and fails randomly.
     */
    @ParameterizedTest
    @MethodSource("providePathAndResponse")
    @DisplayName("Should use AWS Lambda policy and set response as attribute")
    void shouldUseAwsLambdaPolicyAndSetResponseAsAttribute(String path, String responseBody, HttpClient client)
        throws InterruptedException {
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
                assertThat(body.toString()).isEqualTo(responseBody);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(getRequestedFor(urlPathEqualTo("/endpoint")));
        awsLambdaMock.verify(postRequestedFor(urlMatching("/\\d{4}-\\d{2}-\\d{2}/functions/lambda-example/invocations")));
    }

    private static Stream<Arguments> providePath() {
        return Stream.of(
            Arguments.of("/on-request", 0),
            Arguments.of("/on-request-content", 0),
            Arguments.of("/on-response", 1),
            Arguments.of("/on-response-content", 1)
        );
    }

    private static Stream<Arguments> providePathAndResponse() {
        return Stream.of(
            Arguments.of("/on-request", "response from lambda"),
            Arguments.of("/on-response", "response from lambda"),
            Arguments.of("/on-response-content", "response from lambda"),
            Arguments.of("/send-to-consumer", "noContent")
        );
    }
}
