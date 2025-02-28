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
package io.gravitee.policy.aws.lambda.el;

import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LambdaResponse {

    private final InvokeResponse result;
    private final String content;

    public LambdaResponse(final InvokeResponse result) {
        this.result = result;
        this.content = new String(result.payload().asByteArray());
    }

    public int getStatus() {
        return result.statusCode();
    }

    public String getContent() {
        return content;
    }
}
