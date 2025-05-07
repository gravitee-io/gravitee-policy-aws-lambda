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

import static io.gravitee.policy.aws.lambda.configuration.AwsLambdaError.AWS_LAMBDA_INVALID_RESPONSE;
import static io.gravitee.policy.aws.lambda.configuration.AwsLambdaError.AWS_LAMBDA_INVALID_STATUS_CODE;

import io.gravitee.common.util.Maps;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.gravitee.gateway.api.el.EvaluableResponse;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponse;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaError;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.gravitee.policy.aws.lambda.configuration.PolicyScope;
import io.gravitee.policy.aws.lambda.el.LambdaResponse;
import io.gravitee.policy.aws.lambda.invokers.LambdaInvokerV3;
import io.gravitee.policy.aws.lambda.sdk.AWSCredentialsProviderChain;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class AwsLambdaPolicyV3 {

    protected final AwsLambdaPolicyConfiguration configuration;
    protected static final String TEMPLATE_VARIABLE = "lambdaResponse";
    private final LambdaAsyncClient lambdaClient;
    private static final String LAMBDA_RESULT_ATTR = "LAMBDA_RESULT";
    private static final String REQUEST_TEMPLATE_VARIABLE = "request";
    private static final String RESPONSE_TEMPLATE_VARIABLE = "response";

    public AwsLambdaPolicyV3(AwsLambdaPolicyConfiguration configuration) {
        this.configuration = configuration;
        lambdaClient = initLambdaClient();
    }

    @OnRequest
    public void onRequest(ExecutionContext context, PolicyChain chain) {
        if (configuration.getScope() != PolicyScope.REQUEST) {
            chain.doNext(context.request(), context.response());
            return;
        }

        final Context vertxContext = Vertx.currentContext();
        final Invoker originalInvoker = (Invoker) context.getAttribute(ExecutionContext.ATTR_INVOKER);

        invokeLambda(
            context,
            result -> {
                // Dynamically set the default invoker and provide a custom implementation to returns data from lambda function.
                context.setAttribute(
                    ExecutionContext.ATTR_INVOKER,
                    new LambdaInvokerV3(!configuration.isSendToConsumer(), originalInvoker, result)
                );

                // Continue the policy chain.
                vertxContext.runOnContext(v -> chain.doNext(context.request(), context.response()));
            },
            chain::failWith
        );
    }

    @OnResponse
    public void onResponse(ExecutionContext context, PolicyChain chain) {
        if (configuration.getScope() != PolicyScope.RESPONSE) {
            chain.doNext(context.request(), context.response());
            return;
        }

        final Context vertxContext = Vertx.currentContext();

        invokeLambda(
            context,
            result -> {
                if (configuration.isSendToConsumer()) {
                    // Save the lambda result for later reuse in the response content phase (eg: to override the response).
                    context.setAttribute(LAMBDA_RESULT_ATTR, result);
                }

                vertxContext.runOnContext(v -> chain.doNext(context.request(), context.response()));
            },
            chain::failWith
        );
    }

    @OnRequestContent
    public ReadWriteStream<Buffer> onRequestContent(ExecutionContext context, PolicyChain chain) {
        if (configuration.getScope() != PolicyScope.REQUEST_CONTENT) {
            return null;
        }

        final Invoker originalInvoker = (Invoker) context.getAttribute(ExecutionContext.ATTR_INVOKER);
        final LambdaInvokerV3 lambdaInvoker = new LambdaInvokerV3(!configuration.isSendToConsumer(), originalInvoker);
        context.setAttribute(ExecutionContext.ATTR_INVOKER, lambdaInvoker);

        return new BufferedReadWriteStream() {
            final io.gravitee.gateway.api.buffer.Buffer buffer = io.gravitee.gateway.api.buffer.Buffer.buffer();

            @Override
            public SimpleReadWriteStream<Buffer> write(io.gravitee.gateway.api.buffer.Buffer content) {
                buffer.appendBuffer(content);
                return this;
            }

            @Override
            public void end() {
                context
                    .getTemplateEngine()
                    .getTemplateContext()
                    .setVariable(REQUEST_TEMPLATE_VARIABLE, new EvaluableRequest(context.request(), buffer.toString()));

                invokeLambda(
                    context,
                    result -> {
                        if (configuration.isSendToConsumer()) {
                            // Provide the lambda result and let the invoker propagate it to the client.
                            lambdaInvoker.setInvokeResponse(result);
                        }

                        if (buffer.length() > 0) {
                            super.write(buffer);
                        }

                        super.end();
                    },
                    chain::streamFailWith
                );
            }
        };
    }

    @OnResponseContent
    public ReadWriteStream<Buffer> onResponseContent(ExecutionContext context, PolicyChain chain) {
        if (configuration.getScope() != PolicyScope.RESPONSE_CONTENT && configuration.getScope() != PolicyScope.RESPONSE) {
            return null;
        }

        return new BufferedReadWriteStream() {
            final io.gravitee.gateway.api.buffer.Buffer buffer = io.gravitee.gateway.api.buffer.Buffer.buffer();

            @Override
            public SimpleReadWriteStream<Buffer> write(io.gravitee.gateway.api.buffer.Buffer content) {
                buffer.appendBuffer(content);
                return this;
            }

            @Override
            public void end() {
                context
                    .getTemplateEngine()
                    .getTemplateContext()
                    .setVariable(RESPONSE_TEMPLATE_VARIABLE, new EvaluableResponse(context.response(), buffer.toString()));

                final InvokeResponse lambdaResult = (InvokeResponse) context.getAttribute(LAMBDA_RESULT_ATTR);
                context.removeAttribute(LAMBDA_RESULT_ATTR);

                if (configuration.getScope() == PolicyScope.RESPONSE) {
                    // Reuse the lambda response we've got during the response phase and propagate it back to the client.
                    if (configuration.isSendToConsumer() && lambdaResult != null) {
                        super.write(Buffer.buffer(lambdaResult.payload().asByteArray()));
                    } else if (buffer.length() > 0) {
                        super.write(buffer);
                    }
                    super.end();
                } else {
                    invokeLambda(
                        context,
                        result -> {
                            if (configuration.isSendToConsumer()) {
                                super.write(Buffer.buffer(result.payload().asByteArray()));
                            } else if (buffer.length() > 0) {
                                super.write(buffer);
                            }

                            super.end();
                        },
                        chain::streamFailWith
                    );
                }
            }
        };
    }

    protected CompletableFuture<InvokeResponse> invokeLambda(TemplateEngine templateEngine) {
        return CompletableFuture
            .supplyAsync(() -> {
                InvokeRequest.Builder awsRequest = InvokeRequest
                    .builder()
                    .functionName(configuration.getFunction())
                    .invocationType(configuration.getInvocationType())
                    .qualifier(configuration.getQualifier())
                    .logType(configuration.getLogType());

                if (configuration.getPayload() != null && !configuration.getPayload().isEmpty()) {
                    String payload = templateEngine.evalNow(configuration.getPayload(), String.class);
                    awsRequest.payload(SdkBytes.fromUtf8String(payload));
                }

                return awsRequest;
            })
            .thenCompose(awsRequest -> {
                // invoke the lambda function and inspect the result...
                return lambdaClient.invoke(awsRequest.build());
            });
    }

    protected LambdaAsyncClient initLambdaClient() {
        AwsCredentialsProvider awsCredentialsProvider;

        if (configuration.getRoleArn() != null && !configuration.getRoleArn().isEmpty()) {
            awsCredentialsProvider = createSTSCredentialsProvider();
        } else {
            awsCredentialsProvider = getAWSCredentialsProvider();
        }

        return LambdaAsyncClient.builder().credentialsProvider(awsCredentialsProvider).region(Region.of(configuration.getRegion())).build();
    }

    private StsAssumeRoleCredentialsProvider createSTSCredentialsProvider() {
        return StsAssumeRoleCredentialsProvider
            .builder()
            .refreshRequest(() ->
                AssumeRoleRequest.builder().roleArn(configuration.getRoleArn()).roleSessionName(configuration.getRoleSessionName()).build()
            )
            .stsClient(
                StsClient.builder().credentialsProvider(getAWSCredentialsProvider()).region(Region.of(configuration.getRegion())).build()
            )
            .build();
    }

    private AwsCredentialsProvider getAWSCredentialsProvider() {
        AwsBasicCredentials credentials = null;

        if (
            configuration.getAccessKey() != null &&
            !configuration.getAccessKey().isEmpty() &&
            configuration.getSecretKey() != null &&
            !configuration.getSecretKey().isEmpty()
        ) {
            credentials = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
        }

        if (credentials != null) {
            // {@see http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html}
            return StaticCredentialsProvider.create(credentials);
        } else {
            return AWSCredentialsProviderChain.INSTANCE;
        }
    }

    private void invokeLambda(ExecutionContext context, Consumer<InvokeResponse> onSuccess, Consumer<PolicyResult> onError) {
        invokeLambda(context.getTemplateEngine())
            .whenCompleteAsync((InvokeResponse result, Throwable throwable) -> {
                // Lambda will return an HTTP status code will be in the 200 range for successful
                // request, even if an error occurred in the Lambda function itself. Here, we check
                // if an error occurred via getFunctionError() before checking the status code.
                if ("Handled".equals(result.functionError()) || "Unhandled".equals(result.functionError())) {
                    onError.accept(
                        PolicyResult.failure(
                            AWS_LAMBDA_INVALID_RESPONSE.name(),
                            AWS_LAMBDA_INVALID_RESPONSE.getStatusCode(),
                            "An error occurs while invoking lambda function.",
                            Maps
                                .<String, Object>builder()
                                .put("function", configuration.getFunction())
                                .put("region", configuration.getRegion())
                                .put("error", result.functionError())
                                .build()
                        )
                    );
                } else if (result.statusCode() >= 200 && result.statusCode() < 300) {
                    TemplateEngine tplEngine = context.getTemplateEngine();
                    // Put response into template variable for EL
                    tplEngine.getTemplateContext().setVariable(TEMPLATE_VARIABLE, new LambdaResponse(result));

                    // Set context variables
                    if (configuration.getVariables() != null) {
                        configuration
                            .getVariables()
                            .forEach(variable -> {
                                try {
                                    String extValue = (variable.getValue() != null)
                                        ? tplEngine.evalNow(variable.getValue(), String.class)
                                        : null;

                                    context.setAttribute(variable.getName(), extValue);
                                } catch (Exception exception) {
                                    log.error("An error occurs while setting variable {}", variable.getName(), exception);
                                }
                            });
                    }
                    onSuccess.accept(result);
                } else {
                    onError.accept(
                        PolicyResult.failure(
                            AWS_LAMBDA_INVALID_STATUS_CODE.name(),
                            AWS_LAMBDA_INVALID_STATUS_CODE.getStatusCode(),
                            "Invalid status code from lambda function response.",
                            Maps
                                .<String, Object>builder()
                                .put("function", configuration.getFunction())
                                .put("region", configuration.getRegion())
                                .put("error", result.functionError())
                                .build()
                        )
                    );
                }
            })
            .exceptionallyCompose(throwable -> {
                log.error("An error occurs while invoking AWS Lambda function", throwable);
                onError.accept(
                    PolicyResult.failure(
                        AWS_LAMBDA_INVALID_RESPONSE.name(),
                        AWS_LAMBDA_INVALID_RESPONSE.getStatusCode(),
                        "An error occurs while invoking lambda function. Details: [" + throwable.getMessage() + "]",
                        Maps
                            .<String, Object>builder()
                            .put("function", configuration.getFunction())
                            .put("region", configuration.getRegion())
                            .build()
                    )
                );
                return CompletableFuture.failedFuture(throwable);
            });
    }
}
