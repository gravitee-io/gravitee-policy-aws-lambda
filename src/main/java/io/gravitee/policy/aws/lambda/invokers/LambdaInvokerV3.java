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
package io.gravitee.policy.aws.lambda.invokers;

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadStream;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/**
 * Invoker allowing to delegate invocation to the specified invoker or replace the invocation response with the provided lambda response (aka {@link InvokeResponse}).
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class LambdaInvokerV3 implements Invoker {

    private final Invoker delegate;
    private final boolean invokeDelegate;
    private InvokeResponse invokeResponse;

    public LambdaInvokerV3(boolean invokeDelegate, Invoker delegate, InvokeResponse invokeResponse) {
        this.invokeDelegate = invokeDelegate;
        this.delegate = delegate;
        this.invokeResponse = invokeResponse;
    }

    public LambdaInvokerV3(boolean invokeDelegate, Invoker delegate) {
        this.invokeDelegate = invokeDelegate;
        this.delegate = delegate;
    }

    @Override
    public void invoke(ExecutionContext context, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
        // We have to skip the original invoker if isSendToConsumer is true
        if (!invokeDelegate) {
            // Replace the proxy response with the lambda response.
            final ProxyConnection proxyConnection = new LambdaProxyConnection();

            // Return connection to backend
            connectionHandler.handle(proxyConnection);

            // Plug underlying stream to connection stream
            stream.bodyHandler(proxyConnection::write).endHandler(aVoid -> proxyConnection.end());

            // Resume the incoming request to handle content and end
            context.request().resume();
        } else {
            delegate.invoke(context, stream, connectionHandler);
        }
    }

    class LambdaProxyConnection implements ProxyConnection {

        private Handler<ProxyResponse> proxyResponseHandler;
        private Buffer content;

        LambdaProxyConnection() {}

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
            proxyResponseHandler.handle(new LambdaClientResponse());
        }

        @Override
        public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
            this.proxyResponseHandler = responseHandler;
            return this;
        }
    }

    class LambdaClientResponse implements ProxyResponse {

        private final HttpHeaders headers = HttpHeaders.create();

        private Handler<Buffer> bodyHandler;
        private Handler<Void> endHandler;

        LambdaClientResponse() {
            this.init();
        }

        private void init() {
            SdkBytes payload = invokeResponse.payload();

            if (payload != null) {
                headers.set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(payload.asByteArray().length));
            }
        }

        @Override
        public int status() {
            return invokeResponse.statusCode();
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
            SdkBytes payload = invokeResponse.payload();

            if (payload != null) {
                bodyHandler.handle(Buffer.buffer(payload.asByteArray()));
            }

            endHandler.handle(null);
            return this;
        }
    }
}
