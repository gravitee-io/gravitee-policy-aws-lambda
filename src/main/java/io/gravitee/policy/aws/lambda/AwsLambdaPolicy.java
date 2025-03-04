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

import static io.gravitee.gateway.api.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.gravitee.policy.aws.lambda.configuration.AwsLambdaError.AWS_LAMBDA_INVALID_STATUS_CODE;
import static io.gravitee.policy.aws.lambda.configuration.AwsLambdaError.AWS_LAMBDA_TEMPLATE_VARIABLE_EXCEPTION;

import io.gravitee.definition.model.v4.Api;
import io.gravitee.definition.model.v4.ApiType;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.InternalContextAttributes;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.gateway.reactor.ReactableApi;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaError;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.gravitee.policy.aws.lambda.el.LambdaResponse;
import io.gravitee.policy.aws.lambda.invokers.LambdaInvoker;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class AwsLambdaPolicy extends AwsLambdaPolicyV3 implements HttpPolicy {

    public AwsLambdaPolicy(AwsLambdaPolicyConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String id() {
        return "aws-lambda";
    }

    @Override
    public Completable onRequest(HttpPlainExecutionContext ctx) {
        return invokeAndHandleLambda(ctx)
            .flatMapCompletable(invokeResponse -> {
                ReactableApi<?> reactableApi = ctx.getComponent(ReactableApi.class);
                var definition = reactableApi.getDefinition();
                var skipInvoker = definition instanceof Api && ((Api) definition).getType() == ApiType.MESSAGE;

                if (configuration.isSendToConsumer() && !skipInvoker) {
                    // If sendToConsumer is true, use the Lambda invoker instead of the default HTTP invoker.
                    // This bypasses the backend and directly returns the Lambda result.
                    ctx.setInternalAttribute(
                        InternalContextAttributes.ATTR_INTERNAL_INVOKER,
                        new LambdaInvoker(
                            configuration,
                            ctx.getInternalAttribute(InternalContextAttributes.ATTR_INTERNAL_INVOKER),
                            invokeResponse
                        )
                    );
                }

                return Completable.complete();
            });
    }

    @Override
    public Completable onResponse(HttpPlainExecutionContext ctx) {
        return invokeAndHandleLambda(ctx)
            .flatMapCompletable(invokeResponse -> {
                if (configuration.isSendToConsumer()) {
                    Buffer buffer = Buffer.buffer(invokeResponse.payload().asByteArray());
                    ctx.response().headers().remove(CONTENT_LENGTH);
                    ctx.response().status(invokeResponse.statusCode());
                    return ctx.response().onChunks(chunks -> chunks.ignoreElements().andThen(Flowable.just(buffer)));
                }
                return Completable.complete();
            });
    }

    private <T extends HttpBaseExecutionContext> Single<InvokeResponse> invokeAndHandleLambda(T ctx) {
        return Single
            .fromFuture(invokeLambda(ctx.getTemplateEngine()))
            .subscribeOn(Schedulers.io())
            .flatMap(invokeResponse -> {
                log.debug("AWS Lambda function has been invoked successfully");
                String functionError = invokeResponse.functionError();
                if (functionError != null && List.of("Handled", "Unhandled").contains(functionError)) {
                    return processError(ctx, null, AwsLambdaError.AWS_LAMBDA_INVALID_RESPONSE);
                } else if (invokeResponse.statusCode() >= 200 && invokeResponse.statusCode() < 300) {
                    return processSuccess(ctx, invokeResponse);
                }
                return processError(ctx, null, AWS_LAMBDA_INVALID_STATUS_CODE);
            })
            .onErrorResumeNext(throwable -> processError(ctx, throwable, AwsLambdaError.AWS_LAMBDA_INVALID_RESPONSE));
    }

    private <T extends HttpBaseExecutionContext> Single<InvokeResponse> processError(T ctx, Throwable throwable, AwsLambdaError error) {
        log.debug("An error occurs while invoking lambda function", throwable);

        ExecutionFailure failure = new ExecutionFailure(error.getStatusCode())
            .key(error.name())
            .message(throwable != null ? error.getMessageWithDetails(error, throwable) : error.getMessage());

        ctx.metrics().setErrorMessage(error.getMessage());

        return switch (ctx) {
            case HttpMessageExecutionContext messageCtx -> messageCtx
                .interruptMessagesWith(failure)
                .ignoreElements()
                .<InvokeResponse>toMaybe()
                .toSingle();
            case HttpPlainExecutionContext plainCtx -> plainCtx.interruptWith(failure).<InvokeResponse>toMaybe().toSingle();
            default -> Single.error(new IllegalArgumentException("Unsupported context type"));
        };
    }

    private <T extends HttpBaseExecutionContext> Single<InvokeResponse> processSuccess(T ctx, InvokeResponse result) {
        var tplEngine = ctx.getTemplateEngine();
        tplEngine.getTemplateContext().setVariable(TEMPLATE_VARIABLE, new LambdaResponse(result));

        if (configuration.getVariables() != null) {
            for (var variable : configuration.getVariables()) {
                try {
                    String extValue = (variable.getValue() != null) ? tplEngine.evalNow(variable.getValue(), String.class) : null;
                    ctx.setAttribute(variable.getName(), extValue);
                } catch (Exception exception) {
                    return processError(ctx, exception, AWS_LAMBDA_TEMPLATE_VARIABLE_EXCEPTION);
                }
            }
        }
        return Single.just(result);
    }
}
