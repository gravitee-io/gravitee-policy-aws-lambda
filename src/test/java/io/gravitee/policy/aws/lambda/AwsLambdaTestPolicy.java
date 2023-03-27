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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;

/**
 * This AwsLambdaTestPolicy policy has been created to be able to override the AWS Lambda client in order to use WireMock.
 * By default the AwsLambdaPolicy uses the AWS SDK to create the client. This client is created in the initLambdaClient method
 * and configure the endpoint using the configuration credentials and region. Using it, it'll try to call a real AWS Lambda endpoint
 * which will return us a forbidden exception.
 */
public class AwsLambdaTestPolicy extends AwsLambdaPolicy {

    public AwsLambdaTestPolicy(AwsLambdaTestPolicyConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected AWSLambdaAsync initLambdaClient() {
        return AWSLambdaAsyncClientBuilder
            .standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey()))
            )
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    ((AwsLambdaTestPolicyConfiguration) this.configuration).getEndpoint(),
                    this.configuration.getRegion()
                )
            )
            .build();
    }
}
