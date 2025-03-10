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

import com.amazonaws.auth.*;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import io.gravitee.common.http.HttpStatusCode;
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
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.gravitee.policy.aws.lambda.configuration.PolicyScope;
import io.gravitee.policy.aws.lambda.el.LambdaResponse;
import io.gravitee.policy.aws.lambda.sdk.AWSCredentialsProviderChain;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.function.Consumer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AwsLambdaPolicy {

    private static final String AWS_LAMBDA_INVALID_STATUS_CODE = "AWS_LAMBDA_INVALID_STATUS_CODE";
    private static final String AWS_LAMBDA_INVALID_RESPONSE = "AWS_LAMBDA_INVALID_RESPONSE";
    private static final String LAMBDA_RESULT_ATTR = "LAMBDA_RESULT";

    protected final AwsLambdaPolicyConfiguration configuration;

    private final AWSLambdaAsync lambdaClient;

    private static final String TEMPLATE_VARIABLE = "lambdaResponse";

    private static final String REQUEST_TEMPLATE_VARIABLE = "request";
    private static final String RESPONSE_TEMPLATE_VARIABLE = "response";

    public AwsLambdaPolicy(AwsLambdaPolicyConfiguration configuration) {
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
                    new LambdaInvoker(!configuration.isSendToConsumer(), originalInvoker, result)
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
                    if (configuration.isSendToConsumer()) {
                        // Save the lambda result for later reuse in the response content phase (eg: to override the response).
                        context.setAttribute(LAMBDA_RESULT_ATTR, result);
                    }
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
        final LambdaInvoker lambdaInvoker = new LambdaInvoker(!configuration.isSendToConsumer(), originalInvoker);
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
                            lambdaInvoker.setInvokeResult(result);
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

                final InvokeResult lambdaResult = (InvokeResult) context.getAttribute(LAMBDA_RESULT_ATTR);
                context.removeAttribute(LAMBDA_RESULT_ATTR);

                if (configuration.getScope() == PolicyScope.RESPONSE) {
                    // Reuse the lambda response we've got during the response phase and propagate it back to the client.
                    if (configuration.isSendToConsumer() && lambdaResult != null) {
                        super.write(Buffer.buffer(lambdaResult.getPayload().array()));
                    } else if (buffer.length() > 0) {
                        super.write(buffer);
                    }
                    super.end();
                } else {
                    invokeLambda(
                        context,
                        result -> {
                            if (configuration.isSendToConsumer()) {
                                super.write(Buffer.buffer(result.getPayload().array()));
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

    private void invokeLambda(ExecutionContext context, Consumer<InvokeResult> onSuccess, Consumer<PolicyResult> onError) {
        InvokeRequest request = new InvokeRequest()
            .withFunctionName(configuration.getFunction())
            .withInvocationType(configuration.getInvocationType())
            .withQualifier(configuration.getQualifier())
            .withLogType(configuration.getLogType());

        if (configuration.getPayload() != null && !configuration.getPayload().isEmpty()) {
            String payload = context.getTemplateEngine().getValue(configuration.getPayload(), String.class);
            request.withPayload(payload);
        }

        // invoke the lambda function and inspect the result...
        // {@see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/lambda/model/InvokeResult.html}
        lambdaClient.invokeAsync(
            request,
            new AsyncHandler<InvokeRequest, InvokeResult>() {
                @Override
                public void onError(Exception ex) {
                    onError.accept(
                        PolicyResult.failure(
                            AWS_LAMBDA_INVALID_RESPONSE,
                            HttpStatusCode.INTERNAL_SERVER_ERROR_500,
                            "An error occurs while invoking lambda function. Details: [" + ex.getMessage() + "]",
                            Maps
                                .<String, Object>builder()
                                .put("function", configuration.getFunction())
                                .put("region", configuration.getRegion())
                                .put("error", ex.getMessage())
                                .build()
                        )
                    );
                }

                @Override
                public void onSuccess(InvokeRequest request, InvokeResult result) {
                    // Lambda will return an HTTP status code will be in the 200 range for successful
                    // request, even if an error occurred in the Lambda function itself. Here, we check
                    // if an error occurred via getFunctionError() before checking the status code.
                    if ("Handled".equals(result.getFunctionError()) || "Unhandled".equals(result.getFunctionError())) {
                        onError.accept(
                            PolicyResult.failure(
                                AWS_LAMBDA_INVALID_RESPONSE,
                                HttpStatusCode.INTERNAL_SERVER_ERROR_500,
                                "An error occurs while invoking lambda function.",
                                Maps
                                    .<String, Object>builder()
                                    .put("function", configuration.getFunction())
                                    .put("region", configuration.getRegion())
                                    .put("error", result.getFunctionError())
                                    .build()
                            )
                        );
                    } else if (result.getStatusCode() >= 200 && result.getStatusCode() < 300) {
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
                                            ? tplEngine.getValue(variable.getValue(), String.class)
                                            : null;

                                        context.setAttribute(variable.getName(), extValue);
                                    } catch (Exception ex) {
                                        // Do nothing
                                    }
                                });
                        }

                        onSuccess.accept(result);
                    } else {
                        onError.accept(
                            PolicyResult.failure(
                                AWS_LAMBDA_INVALID_STATUS_CODE,
                                HttpStatusCode.BAD_REQUEST_400,
                                "Invalid status code from lambda function response.",
                                Maps
                                    .<String, Object>builder()
                                    .put("function", configuration.getFunction())
                                    .put("region", configuration.getRegion())
                                    .put("statusCode", result.getStatusCode())
                                    .build()
                            )
                        );
                    }
                }
            }
        );
    }

    protected AWSLambdaAsync initLambdaClient() {
        AWSCredentialsProvider awsCredentialsProvider;

        if (configuration.getRoleArn() != null && !configuration.getRoleArn().isEmpty()) {
            awsCredentialsProvider = createSTSCredentialsProvider();
        } else {
            awsCredentialsProvider = getAWSCredentialsProvider();
        }

        return AWSLambdaAsyncClientBuilder.standard().withCredentials(awsCredentialsProvider).withRegion(configuration.getRegion()).build();
    }

    private AWSCredentialsProvider createSTSCredentialsProvider() {
        return new STSAssumeRoleSessionCredentialsProvider.Builder(configuration.getRoleArn(), configuration.getRoleSessionName())
            .withStsClient(
                AWSSecurityTokenServiceClientBuilder
                    .standard()
                    .withCredentials(getAWSCredentialsProvider())
                    .withRegion(configuration.getRegion())
                    .build()
            )
            .build();
    }

    private AWSCredentialsProvider getAWSCredentialsProvider() {
        BasicAWSCredentials credentials = null;

        if (
            configuration.getAccessKey() != null &&
            !configuration.getAccessKey().isEmpty() &&
            configuration.getSecretKey() != null &&
            !configuration.getSecretKey().isEmpty()
        ) {
            credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        }

        if (credentials != null) {
            // {@see http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html}
            return new AWSStaticCredentialsProvider(credentials);
        } else {
            return AWSCredentialsProviderChain.getInstance();
        }
    }
}
