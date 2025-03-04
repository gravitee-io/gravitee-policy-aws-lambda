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

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.http.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.invoker.HttpInvoker;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/**
 * Invoker allowing to delegate invocation to the specified invoker or replace the invocation response
 * with the provided lambda response (aka {@link InvokeResponse}).
 *
 * @author GraviteeSource Team
 */
@Setter
@Getter
@Slf4j
public class LambdaInvoker implements HttpInvoker {

    private final HttpInvoker delegate;
    private final AwsLambdaPolicyConfiguration configuration;
    private InvokeResponse invokeResponse;

    public LambdaInvoker(AwsLambdaPolicyConfiguration configuration, HttpInvoker delegate, InvokeResponse invokeResponse) {
        this.configuration = configuration;
        this.delegate = delegate;
        this.invokeResponse = invokeResponse;
    }

    @Override
    public String getId() {
        return "aws-lambda-invoker";
    }

    @Override
    public Completable invoke(HttpExecutionContext ctx) {
        return Completable.defer(() -> {
            if (configuration.isSendToConsumer()) {
                ctx.response().status(invokeResponse.statusCode());
                ctx
                    .response()
                    .chunks(
                        ctx
                            .response()
                            .chunks()
                            .ignoreElements()
                            .andThen(Flowable.just(Buffer.buffer(invokeResponse.payload().asByteArray())))
                    );
            }
            return Completable.complete();
        });
    }
}
