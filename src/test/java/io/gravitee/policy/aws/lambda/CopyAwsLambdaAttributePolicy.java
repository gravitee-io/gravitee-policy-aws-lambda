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

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.annotations.OnResponseContent;

public class CopyAwsLambdaAttributePolicy {

    public static final String COPY_AWS_LAMBDA_ATTRIBUTE_POLICY = "copy-aws-lambda-attribute";
    public static final String NO_AWS_LAMBDA_CONTENT_ATTRIBUTE = "noContent";

    @OnResponseContent
    public ReadWriteStream<Buffer> onResponseContent(ExecutionContext context) {
        return new BufferedReadWriteStream() {
            @Override
            public SimpleReadWriteStream<Buffer> write(Buffer content) {
                return this;
            }

            @Override
            public void end() {
                String content = NO_AWS_LAMBDA_CONTENT_ATTRIBUTE;
                final Object awsContent = context.getAttribute("lambdaResponse");
                if (awsContent != null) {
                    content = awsContent.toString();
                }
                super.write(Buffer.buffer(content));
                super.end();
            }
        };
    }
}
