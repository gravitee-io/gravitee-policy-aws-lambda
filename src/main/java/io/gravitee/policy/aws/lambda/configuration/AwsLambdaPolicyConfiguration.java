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

import io.gravitee.policy.api.PolicyConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.LogType;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AwsLambdaPolicyConfiguration implements PolicyConfiguration {

    private PolicyScope scope = PolicyScope.REQUEST;

    private String region = "us-east-1";

    private String accessKey, secretKey;

    private String function;

    private String payload;

    private String invocationType = InvocationType.REQUEST_RESPONSE.toString();

    private String qualifier;

    private String roleArn;

    private String roleSessionName = "gravitee";

    private String logType = LogType.NONE.toString();

    private List<Variable> variables = new ArrayList<>();

    private boolean sendToConsumer;
}
