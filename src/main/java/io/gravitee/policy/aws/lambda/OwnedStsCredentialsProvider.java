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

import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.utils.SdkAutoCloseable;

/**
 * Owns both the STS assume-role credentials provider and the {@link StsClient} passed into it.
 * AWS SDK {@link StsAssumeRoleCredentialsProvider#close()} stops async credential refresh but does
 * not close a caller-supplied {@link StsClient}; eviction of a cached {@code LambdaAsyncClient}
 * closes this provider via {@code AttributeMap} and must therefore close the STS client as well.
 */
final class OwnedStsCredentialsProvider implements AwsCredentialsProvider, SdkAutoCloseable {

    private final StsAssumeRoleCredentialsProvider credentialsProvider;
    private final StsClient stsClient;
    private final AtomicBoolean closed = new AtomicBoolean();

    OwnedStsCredentialsProvider(StsAssumeRoleCredentialsProvider credentialsProvider, StsClient stsClient) {
        this.credentialsProvider = credentialsProvider;
        this.stsClient = stsClient;
    }

    @Override
    public AwsCredentials resolveCredentials() {
        return credentialsProvider.resolveCredentials();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            credentialsProvider.close();
        } finally {
            stsClient.close();
        }
    }
}
