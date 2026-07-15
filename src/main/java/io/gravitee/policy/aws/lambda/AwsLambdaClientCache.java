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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.gravitee.policy.aws.lambda.configuration.AwsLambdaPolicyConfiguration;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

@Slf4j
final class AwsLambdaClientCache {

    private static final int MAX_CACHE_SIZE = Integer.getInteger("gravitee.aws-lambda.client-cache.size", 128);

    private static final Cache<LambdaClientCacheKey, LambdaAsyncClient> CACHE = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .removalListener(AwsLambdaClientCache::onRemoval)
        .build();

    private AwsLambdaClientCache() {}

    static LambdaAsyncClient get(AwsLambdaPolicyConfiguration config, Function<AwsLambdaPolicyConfiguration, LambdaAsyncClient> factory) {
        LambdaClientCacheKey key = LambdaClientCacheKey.from(config);
        return CACHE.get(key, ignored -> factory.apply(config));
    }

    static void invalidateAll() {
        CACHE.invalidateAll();
        CACHE.cleanUp();
    }

    private static void onRemoval(LambdaClientCacheKey key, LambdaAsyncClient client, RemovalCause cause) {
        if (client == null || cause == RemovalCause.REPLACED) {
            return;
        }
        closeQuietly(client);
        log.debug("Closed evicted AWS Lambda client for region [{}], cause [{}]", key.region(), cause);
    }

    private static void closeQuietly(LambdaAsyncClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Failed to close evicted AWS Lambda client", e);
        }
    }
}
