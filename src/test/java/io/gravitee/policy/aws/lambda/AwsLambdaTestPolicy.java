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

import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * This AwsLambdaTestPolicy policy has been created to be able to override the AWS Lambda client in order to use WireMock.
 * By default the AwsLambdaPolicy uses the AWS SDK to create the client. This client is created in the initLambdaClient method
 * and configure the endpoint using the configuration credentials and region. Using it, it'll try to call a real AWS Lambda endpoint
 * which will return us a forbidden exception.
 */
@Slf4j
public class AwsLambdaTestPolicy extends AwsLambdaPolicy {

    private final AwsLambdaTestPolicyConfiguration configuration;
    public static final String AWS_LAMBDA_TEST_POLICY = "aws-lambda-test-policy";

    public AwsLambdaTestPolicy(AwsLambdaTestPolicyConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    @Override
    public String id() {
        return AWS_LAMBDA_TEST_POLICY;
    }

    @Override
    protected LambdaAsyncClient initLambdaClient(AwsLambdaPolicyConfiguration config) {
        log.debug("Initializing AWS Lambda Async Client {} {}", config.getAccessKey(), config.getSecretKey());

        return LambdaAsyncClient
            .builder()
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
            .endpointOverride(URI.create((this.configuration.getEndpoint())))
            .region(Region.of(config.getRegion()))
            .build();
    }
}
