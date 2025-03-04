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

import com.graviteesource.entrypoint.http.get.HttpGetEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.PersistentMockEndpointConnectorFactory;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.gateway.reactor.ReactableApi;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

@GatewayTest
@DeployApi(
    {
        "/apis/v4/aws-lambda-proxy-request.json",
        "/apis/v4/aws-lambda-proxy-response.json",
        "/apis/v4/aws-lambda-message-request.json",
        "/apis/v4/aws-lambda-message-response.json",
    }
)
public class AwsLambdaPolicyV4IntegrationTest extends AwsLambdaPolicyIntegrationTest {

    @Override
    public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
        reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
    }

    @Override
    public void configureApi(ReactableApi<?> api, Class<?> apiDefinitionClass) {
        if (apiDefinitionClass.isAssignableFrom(io.gravitee.definition.model.v4.Api.class)) {
            var definition = (io.gravitee.definition.model.v4.Api) api.getDefinition();
            definition
                .getFlows()
                .forEach(flow ->
                    Stream
                        .concat(flow.getRequest().stream(), flow.getResponse().stream())
                        .filter(step -> AwsLambdaTestPolicy.AWS_LAMBDA_TEST_POLICY.equals(step.getPolicy()))
                        .forEach(step ->
                            step.setConfiguration(step.getConfiguration().replace("http://localhost:9999", awsLambdaMock.baseUrl()))
                        )
                );
        }
    }

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
        entrypoints.putIfAbsent("http-get", EntrypointBuilder.build("http-get", HttpGetEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", PersistentMockEndpointConnectorFactory.class));
    }

    @Override
    protected Stream<Arguments> providePath() {
        return Stream.of(
            Arguments.of("/proxy-request", 0),
            Arguments.of("/proxy-response", 1),
            Arguments.of("/message-request", 0),
            Arguments.of("/message-response", 0)
        );
    }

    @Override
    public Stream<Arguments> providePathAndResponse() {
        return Stream.of(Arguments.of("/proxy-request", "response from lambda"), Arguments.of("/proxy-response", "response from lambda"));
    }
}
