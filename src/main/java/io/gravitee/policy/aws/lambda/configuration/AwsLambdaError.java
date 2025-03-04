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
package io.gravitee.policy.aws.lambda.configuration;

import io.gravitee.common.http.HttpStatusCode;
import lombok.Getter;

@Getter
public enum AwsLambdaError {
    AWS_LAMBDA_INVALID_RESPONSE(HttpStatusCode.INTERNAL_SERVER_ERROR_500, "An error occurs while invoking lambda function."),
    AWS_LAMBDA_INVALID_STATUS_CODE(HttpStatusCode.BAD_REQUEST_400, "Invalid status code from lambda function response."),
    AWS_LAMBDA_TEMPLATE_VARIABLE_EXCEPTION(HttpStatusCode.INTERNAL_SERVER_ERROR_500, "An error occurs while processing context variables.");

    private final int statusCode;
    private final String message;

    AwsLambdaError(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public String getMessageWithDetails(AwsLambdaError error, Throwable throwable) {
        return error.getMessage() + " Details: [" + throwable.getCause().getMessage() + "]";
    }
}
