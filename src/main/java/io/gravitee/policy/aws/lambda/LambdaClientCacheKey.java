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
import io.gravitee.policy.aws.lambda.configuration.SslConfiguration;

/**
 * Identity for {@link software.amazon.awssdk.services.lambda.LambdaAsyncClient} instances.
 * Includes every configuration field that affects client construction.
 */
record LambdaClientCacheKey(
    String region,
    String accessKey,
    String secretKey,
    String roleArn,
    String roleSessionName,
    Integer connectionTimeoutMs,
    Integer readTimeoutMs,
    Integer apiCallAttemptTimeoutMs,
    Integer apiCallTimeoutMs,
    boolean sslTrustAll,
    String sslTrustStoreType,
    String sslTrustStorePath,
    String sslTrustStorePassword,
    String sslTrustStoreContent
) {
    static LambdaClientCacheKey from(AwsLambdaPolicyConfiguration config) {
        SslConfiguration ssl = config.getSsl();
        return new LambdaClientCacheKey(
            config.getRegion(),
            config.getAccessKey(),
            config.getSecretKey(),
            config.getRoleArn(),
            config.getRoleSessionName(),
            config.getConnectionTimeoutMs(),
            config.getReadTimeoutMs(),
            config.getApiCallAttemptTimeoutMs(),
            config.getApiCallTimeoutMs(),
            ssl != null && ssl.isTrustAll(),
            ssl != null ? ssl.getTrustStoreType() : null,
            ssl != null ? ssl.getTrustStorePath() : null,
            ssl != null ? ssl.getTrustStorePassword() : null,
            ssl != null ? ssl.getTrustStoreContent() : null
        );
    }

    @Override
    public String toString() {
        return "LambdaClientCacheKey[region=" + region + ", roleArn=" + roleArn + "]";
    }
}
