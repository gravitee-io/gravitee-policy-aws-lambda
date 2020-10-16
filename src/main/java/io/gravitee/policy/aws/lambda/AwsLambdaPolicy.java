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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.Maps;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadStream;
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
import io.gravitee.policy.aws.lambda.el.EvaluableRequest;
import io.gravitee.policy.aws.lambda.el.EvaluableResponse;
import io.gravitee.policy.aws.lambda.el.LambdaResponse;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AwsLambdaPolicy {

    private final static String AWS_LAMBDA_INVALID_STATUS_CODE = "AWS_LAMBDA_INVALID_STATUS_CODE";
    private final static String AWS_LAMBDA_INVALID_RESPONSE = "AWS_LAMBDA_INVALID_RESPONSE";

    private final AwsLambdaPolicyConfiguration configuration;

    private static AWSLambdaAsync lambdaClient;

    private final static String TEMPLATE_VARIABLE = "lambdaResponse";

    private final static String REQUEST_TEMPLATE_VARIABLE = "request";
    private final static String RESPONSE_TEMPLATE_VARIABLE = "response";

    public AwsLambdaPolicy(AwsLambdaPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(ExecutionContext context, PolicyChain chain) {
        execute(context, chain);
    }

    @OnResponse
    public void onResponse(ExecutionContext context, PolicyChain chain) {
        execute(context, chain);
    }

    private void execute(ExecutionContext context, PolicyChain chain) {
        if (configuration.getScope() == PolicyScope.RESPONSE || configuration.getScope() == PolicyScope.REQUEST) {
            invokeLambda(context, result -> {
                if (configuration.isSendToConsumer()) {
                    // Dynamically set the default invoker and provide a custom implementation
                    // to returns data from lambda function.
                    context.setAttribute(ExecutionContext.ATTR_INVOKER, new LambdaInvoker(result));
                }

                chain.doNext(context.request(), context.response());
            }, chain::failWith);
        } else {
            chain.doNext(context.request(), context.response());
        }
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(ExecutionContext context, PolicyChain policyChain) {
        if (configuration.getScope() == PolicyScope.REQUEST_CONTENT) {
            return createStream(PolicyScope.REQUEST_CONTENT, context, policyChain);
        }

        return null;
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(ExecutionContext context, PolicyChain policyChain) {
        if (configuration.getScope() == PolicyScope.RESPONSE_CONTENT) {
            return createStream(PolicyScope.RESPONSE_CONTENT, context, policyChain);
        }

        return null;
    }

    private ReadWriteStream createStream(PolicyScope scope, ExecutionContext context, PolicyChain policyChain) {
        return new BufferedReadWriteStream() {

            io.gravitee.gateway.api.buffer.Buffer buffer = io.gravitee.gateway.api.buffer.Buffer.buffer();

            @Override
            public SimpleReadWriteStream<Buffer> write(io.gravitee.gateway.api.buffer.Buffer content) {
                buffer.appendBuffer(content);
                return this;
            }

            @Override
            public void end() {
                context.getTemplateEngine().getTemplateContext()
                        .setVariable(REQUEST_TEMPLATE_VARIABLE, new EvaluableRequest(context.request(),
                                (scope == PolicyScope.REQUEST_CONTENT) ? buffer.toString() : null));

                context.getTemplateEngine().getTemplateContext()
                        .setVariable(RESPONSE_TEMPLATE_VARIABLE, new EvaluableResponse(context.response(),
                                (scope == PolicyScope.RESPONSE_CONTENT) ? buffer.toString() : null));

                invokeLambda(context, result -> {
                    if (buffer.length() > 0) {
                        super.write(buffer);
                    }

                    super.end();
                }, policyChain::streamFailWith);
            }
        };
    }

    private void invokeLambda(ExecutionContext context, Consumer<InvokeResult> onSuccess, Consumer<PolicyResult> onError) {
        AWSLambdaAsync lambdaClient = getLambdaClient();

        InvokeRequest request = new InvokeRequest()
                .withFunctionName(configuration.getFunction());

        if (configuration.getPayload() != null && !configuration.getPayload().isEmpty()) {
            String payload = context.getTemplateEngine().getValue(configuration.getPayload(), String.class);
            request.withPayload(payload);
        }

        // invoke the lambda function and inspect the result...
        // {@see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/lambda/model/InvokeResult.html}
        lambdaClient.invokeAsync(request, new AsyncHandler<InvokeRequest, InvokeResult>() {
            @Override
            public void onError(Exception ex) {
                onError.accept(PolicyResult.failure(
                        AWS_LAMBDA_INVALID_RESPONSE,
                        HttpStatusCode.INTERNAL_SERVER_ERROR_500,
                        "An error occurs while invoking lambda function.",
                        Maps.<String, Object>builder()
                                .put("function", configuration.getFunction())
                                .put("region", configuration.getRegion())
                                .put("error", ex.getMessage())
                                .build()));
            }

            @Override
            public void onSuccess(InvokeRequest request, InvokeResult result) {
                // Lambda will return an HTTP status code will be in the 200 range for successful
                // request, even if an error occurred in the Lambda function itself. Here, we check
                // if an error occurred via getFunctionError() before checking the status code.
                if ("Handled".equals(result.getFunctionError()) || "Unhandled".equals(result.getFunctionError())) {
                    onError.accept(PolicyResult.failure(
                            AWS_LAMBDA_INVALID_RESPONSE,
                            HttpStatusCode.INTERNAL_SERVER_ERROR_500,
                            "An error occurs while invoking lambda function.",
                            Maps.<String, Object>builder()
                                    .put("function", configuration.getFunction())
                                    .put("region", configuration.getRegion())
                                    .put("error", result.getFunctionError())
                                    .build()));
                } else if (result.getStatusCode() >= 200 && result.getStatusCode() < 300) {
                    TemplateEngine tplEngine = context.getTemplateEngine();

                    // Put response into template variable for EL
                    tplEngine.getTemplateContext()
                            .setVariable(TEMPLATE_VARIABLE, new LambdaResponse(result));

                    // Set context variables
                    if (configuration.getVariables() != null) {
                        configuration.getVariables().forEach(variable -> {
                            try {
                                String extValue = (variable.getValue() != null) ?
                                        tplEngine.getValue(variable.getValue(), String.class) : null;

                                context.setAttribute(variable.getName(), extValue);
                            } catch (Exception ex) {
                                // Do nothing
                            }
                        });
                    }

                    onSuccess.accept(result);
                } else {
                    onError.accept(PolicyResult.failure(
                            AWS_LAMBDA_INVALID_STATUS_CODE,
                            HttpStatusCode.BAD_REQUEST_400,
                            "Invalid status code from lambda function response.",
                            Maps.<String, Object>builder()
                                    .put("function", configuration.getFunction())
                                    .put("region", configuration.getRegion())
                                    .put("statusCode", result.getStatusCode())
                                    .build()));
                }
            }
        });
    }

    private AWSLambdaAsync getLambdaClient() {
        if (lambdaClient == null) {
            // initialize the lambda client
            AWSLambdaAsyncClientBuilder clientBuilder;
            BasicAWSCredentials credentials = null;

            if (configuration.getAccessKey() != null && !configuration.getAccessKey().isEmpty() &&
                    configuration.getSecretKey() != null && !configuration.getSecretKey().isEmpty()) {
                credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
            }

            if (credentials != null) {
                // {@see http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html}
                clientBuilder = AWSLambdaAsyncClientBuilder.standard()
                        .withCredentials(new AWSStaticCredentialsProvider(credentials))
                        .withRegion(configuration.getRegion());
            } else {
                clientBuilder = AWSLambdaAsyncClientBuilder.standard()
                        .withRegion(configuration.getRegion());
            }

            lambdaClient = clientBuilder.build();
        }

        return lambdaClient;
    }

    class LambdaInvoker implements Invoker {

        private final InvokeResult result;

        LambdaInvoker(final InvokeResult result) {
            this.result = result;
        }

        @Override
        public void invoke(ExecutionContext context, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
            final ProxyConnection proxyConnection = new LambdaProxyConnection(result);

            // Return connection to backend
            connectionHandler.handle(proxyConnection);

            // Plug underlying stream to connection stream
            stream
                    .bodyHandler(proxyConnection::write)
                    .endHandler(aVoid -> proxyConnection.end());

            // Resume the incoming request to handle content and end
            context.request().resume();
        }
    }

    class LambdaProxyConnection implements ProxyConnection {

        private final InvokeResult result;
        private Handler<ProxyResponse> proxyResponseHandler;
        private Buffer content;

        LambdaProxyConnection(final InvokeResult result) {
            this.result = result;
        }

        @Override
        public ProxyConnection write(Buffer chunk) {
            if (content == null) {
                content = Buffer.buffer();
            }
            content.appendBuffer(chunk);
            return this;
        }

        @Override
        public void end() {
            proxyResponseHandler.handle(
                    new LambdaClientResponse(result));
        }

        @Override
        public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
            this.proxyResponseHandler = responseHandler;
            return this;
        }
    }

    class LambdaClientResponse implements ProxyResponse {

        private final InvokeResult result;

        private final HttpHeaders headers = new HttpHeaders();

        private Handler<Buffer> bodyHandler;
        private Handler<Void> endHandler;

        LambdaClientResponse(final InvokeResult result) {
            this.result = result;
            this.init();
        }

        private void init() {
            ByteBuffer payload = result.getPayload();

            if (payload != null) {
                headers.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(payload.array().length));
            }
        }

        @Override
        public int status() {
            return result.getStatusCode();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public ProxyResponse bodyHandler(Handler<Buffer> bodyHandler) {
            this.bodyHandler = bodyHandler;
            return this;
        }

        @Override
        public ProxyResponse endHandler(Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;
        }

        @Override
        public ReadStream<Buffer> resume() {
            ByteBuffer payload = result.getPayload();

            if (payload != null) {
                bodyHandler.handle(Buffer.buffer(payload.array()));
            }

            endHandler.handle(null);
            return this;
        }
    }
}
