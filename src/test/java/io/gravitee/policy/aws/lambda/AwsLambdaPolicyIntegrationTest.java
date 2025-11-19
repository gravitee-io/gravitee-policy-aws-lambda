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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.graviteesource.secretprovider.hcvault.HCVaultSecretProvider;
import com.graviteesource.secretprovider.hcvault.HCVaultSecretProviderFactory;
import com.graviteesource.secretprovider.hcvault.config.manager.VaultConfig;
import com.graviteesource.service.secrets.SecretsService;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.apim.gateway.tests.sdk.secrets.SecretProviderBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.service.AbstractService;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.reactor.ReactableApi;
import io.gravitee.node.secrets.plugins.SecretProviderPlugin;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.secrets.api.plugin.SecretManagerConfiguration;
import io.gravitee.secrets.api.plugin.SecretProviderFactory;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.vault.VaultContainer;

@GatewayTest
@DeployApi(
    {
        "/apis/v2/aws-lambda-on-request.json",
        "/apis/v2/aws-lambda-on-request-content.json",
        "/apis/v2/aws-lambda-on-response.json",
        "/apis/v2/aws-lambda-on-response-content.json",
        "/apis/v2/aws-lambda-with-send-to-consumer.json",
        "/apis/v2/aws-lambda-secret-support.json",
    }
)
public class AwsLambdaPolicyIntegrationTest extends AbstractPolicyTest<AwsLambdaTestPolicy, AwsLambdaTestPolicyConfiguration> {

    private static final String VAULT_TOKEN = UUID.randomUUID().toString();

    @Container
    protected final VaultContainer vaultContainer = new VaultContainer<>("hashicorp/vault:1.13.3").withVaultToken(VAULT_TOKEN);

    protected WireMockServer awsLambdaMock;

    public AwsLambdaPolicyIntegrationTest() {
        vaultContainer.start();
        awsLambdaMock = new WireMockServer(wireMockConfig().dynamicPort());
        awsLambdaMock.start();
    }

    @AfterAll
    public void tearDown() {
        vaultContainer.close();

        if (null != awsLambdaMock) {
            awsLambdaMock.stop();
        }
    }

    @Override
    public void configurePolicies(Map<String, PolicyPlugin> policies) {
        policies.putIfAbsent(
            CopyAwsLambdaAttributePolicy.COPY_AWS_LAMBDA_ATTRIBUTE_POLICY,
            PolicyBuilder.build(CopyAwsLambdaAttributePolicy.COPY_AWS_LAMBDA_ATTRIBUTE_POLICY, CopyAwsLambdaAttributePolicy.class)
        );
        policies.putIfAbsent(
            AwsLambdaTestPolicy.AWS_LAMBDA_TEST_POLICY,
            PolicyBuilder.build(
                AwsLambdaTestPolicy.AWS_LAMBDA_TEST_POLICY,
                AwsLambdaTestPolicy.class,
                AwsLambdaTestPolicyConfiguration.class
            )
        );
    }

    @Override
    public void configureSecretProviders(
        Set<SecretProviderPlugin<? extends SecretProviderFactory<?>, ? extends SecretManagerConfiguration>> secretProviderPlugins
    ) {
        secretProviderPlugins.add(
            SecretProviderBuilder.build(HCVaultSecretProvider.PLUGIN_ID, HCVaultSecretProviderFactory.class, VaultConfig.class)
        );
    }

    @Override
    public void configureServices(Set<Class<? extends AbstractService<?>>> services) {
        super.configureServices(services);
        services.add(SecretsService.class);
    }

    @Override
    protected void configureGateway(GatewayConfigurationBuilder configurationBuilder) {
        super.configureGateway(configurationBuilder);
        configurationBuilder.set("api.jupiterMode.enabled", "true");
        configureVault(configurationBuilder);
    }

    private void configureVault(GatewayConfigurationBuilder configurationBuilder) {
        String token = createToken();
        setConfiguration(configurationBuilder, token);
        createKeyValuePairs();
    }

    private String createToken() {
        try {
            return vaultContainer.execInContainer("vault", "token", "create", "-period=10m", "-field", "token").getStdout();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void createKeyValuePairs() {
        try {
            vaultContainer.execInContainer(
                "vault",
                "kv",
                "put",
                "secret/aws",
                "accessKey=" + "test_access_key",
                "secretKey=" + "test_secret_key",
                "roleArn=" + "test_role_arn"
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void setConfiguration(GatewayConfigurationBuilder configurationBuilder, String token) {
        configurationBuilder.setYamlProperty("secrets.vault.enabled", true);
        configurationBuilder.setYamlProperty("secrets.vault.host", vaultContainer.getHost());
        configurationBuilder.setYamlProperty("secrets.vault.port", vaultContainer.getMappedPort(8200));
        configurationBuilder.setYamlProperty("secrets.vault.ssl.enabled", false);
        configurationBuilder.setYamlProperty("secrets.vault.auth.method", "token");
        configurationBuilder.setYamlProperty("secrets.vault.auth.config.token", token);

        configurationBuilder.setYamlProperty("api.secrets.providers[0].plugin", "vault");
        configurationBuilder.setYamlProperty("api.secrets.providers[0].configuration.enabled", true);
        configurationBuilder.setYamlProperty("api.secrets.providers[0].configuration.host", vaultContainer.getHost());
        configurationBuilder.setYamlProperty("api.secrets.providers[0].configuration.port", vaultContainer.getMappedPort(8200));
        configurationBuilder.setYamlProperty("api.secrets.providers[0].configuration.ssl.enabled", "false");
        configurationBuilder.setYamlProperty("api.secrets.providers[0].configuration.auth.method", "token");
        configurationBuilder.setYamlProperty("api.secrets.providers[0].configuration.auth.config.token", token);
    }

    @Override
    public void configureApi(ReactableApi<?> api, Class<?> apiDefinitionClass) {
        if (apiDefinitionClass.isAssignableFrom(Api.class)) {
            ((Api) api.getDefinition()).setExecutionMode(ExecutionMode.V3);
            ((Api) api.getDefinition()).getFlows()
                .forEach(flow -> {
                    Stream
                        .concat(flow.getPre().stream(), flow.getPost().stream())
                        .filter(policy -> AwsLambdaTestPolicy.AWS_LAMBDA_TEST_POLICY.equals(policy.getPolicy()))
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
    void shouldUseAwsLambdaPolicyAndReturnError(String path, int endpointCallCount, HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));
        awsLambdaMock.stubFor(post(anyUrl()).willReturn(forbidden()));

        client
            .rxRequest(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
                return response.toFlowable();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
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
    void shouldUseAwsLambdaPolicyAndSetResponseAsAttribute(String path, String responseBody, HttpClient client) {
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
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body.toString()).isEqualTo(responseBody);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(getRequestedFor(urlPathEqualTo("/endpoint")));
        awsLambdaMock.verify(postRequestedFor(urlMatching("/\\d{4}-\\d{2}-\\d{2}/functions/lambda-example/invocations")));
    }

    protected Stream<Arguments> providePath() {
        return Stream.of(
            Arguments.of("/on-request", 0),
            Arguments.of("/on-request-content", 0),
            Arguments.of("/on-response", 1),
            Arguments.of("/on-response-content", 1),
            Arguments.of("/secret-support", 0)
        );
    }

    protected Stream<Arguments> providePathAndResponse() {
        return Stream.of(
            Arguments.of("/on-request", "response from lambda"),
            Arguments.of("/on-response", "response from lambda"),
            Arguments.of("/on-response-content", "response from lambda"),
            Arguments.of("/send-to-consumer", "noContent"),
            Arguments.of("/secret-support", "response from lambda")
        );
    }
}
