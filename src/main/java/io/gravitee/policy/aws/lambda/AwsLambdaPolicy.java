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

import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import io.gravitee.policy.v3.aws.lambda.AwsLambdaPolicyV3;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AwsLambdaPolicy extends AwsLambdaPolicyV3 implements HttpPolicy {

    public AwsLambdaPolicy(AwsLambdaPolicyConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String id() {
        return "aws-lambda";
    }
}
