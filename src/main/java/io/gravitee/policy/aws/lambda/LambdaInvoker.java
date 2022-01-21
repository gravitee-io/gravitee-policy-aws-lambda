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

import com.amazonaws.services.lambda.model.InvokeResult;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadStream;
import java.nio.ByteBuffer;

/**
 * Invoker allowing to delegate invocation to the specified invoker or replace the invocation response with the provided lambda respinse (aka {@link InvokeResult}).
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LambdaInvoker implements Invoker {

    private final Invoker delegate;
    private final boolean invokeDelegate;

    private InvokeResult invokeResult;

    LambdaInvoker(boolean invokeDelegate, Invoker delegate, InvokeResult invokeResult) {
        this.invokeDelegate = invokeDelegate;
        this.delegate = delegate;
        this.invokeResult = invokeResult;
    }

    public LambdaInvoker(boolean invokeDelegate, Invoker delegate) {
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

    public InvokeResult getInvokeResult() {
        return invokeResult;
    }

    public void setInvokeResult(InvokeResult invokeResult) {
        this.invokeResult = invokeResult;
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
            ByteBuffer payload = invokeResult.getPayload();

            if (payload != null) {
                headers.set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(payload.array().length));
            }
        }

        @Override
        public int status() {
            return invokeResult.getStatusCode();
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
            ByteBuffer payload = invokeResult.getPayload();

            if (payload != null) {
                bodyHandler.handle(Buffer.buffer(payload.array()));
            }

            endHandler.handle(null);
            return this;
        }
    }
}
