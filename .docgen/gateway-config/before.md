### Gateway tuning (advanced)

These are **JVM system properties** on the Gateway process (for example via `JAVA_OPTS`). They are not fields in the policy UI or in `gravitee.yml`.

| System property | Default | Description |
| --- | --- | --- |
| `gravitee.aws-lambda.client-cache.size` | `128` | Maximum number of cached `LambdaAsyncClient` instances (keyed by policy AWS settings). |
| `gravitee.aws-lambda.blocking-pool.size` | `max(4, available processors)` | Size of the dedicated worker pool used for AWS client setup and synchronous credential work (for example STS AssumeRole refresh) so it does not run on the Vert.x event loop. |
