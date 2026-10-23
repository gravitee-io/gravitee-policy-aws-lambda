
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# AWS Lambda

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-aws-lambda/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-aws-lambda/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-aws-lambda/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-aws-lambda.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-aws-lambda)

## Overview
You can use the `aws-lambda` policy to invoke an AWS Lambda function instead of, or in addition to, the backend.

By default, the Lambda function is called in addition to the backend, meaning the consumer does not receive the response from the Lambda function. Enable `sendToConsumer` to return the Lambda response to the consumer without calling the backend.

The Lambda response is available in the Gravitee Expression Language through the `#lambdaResponse` variable. Use `variables` to store values extracted from it in the execution context.

Credentials (`accessKey`, `secretKey`, `roleArn`) can be stored in a secret manager and referenced with the Gravitee Expression Language. When `roleArn` is set, the policy relies on AWS Security Token Service (STS) to assume the role and create temporary credentials.

**Warning:** Version 1.3.0 of this policy should not be used.




## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key |
| ---  |
| AWS_LAMBDA_INVALID_RESPONSE |
| AWS_LAMBDA_INVALID_STATUS_CODE |
| AWS_LAMBDA_TEMPLATE_VARIABLE_EXCEPTION |



## Phases
The `aws-lambda` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`
* `MESSAGE`
* `MCP PROXY`

### Supported flow phases:

* Request
* Response

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM| Java version |
| --- | --- | ---  |
|3.x|4.8.x and above|21 |
|2.x|4.7.x|21 |
|1.x|3.x to 4.6.x|17 |



## Configuration
### Gateway configuration
### Gateway tuning (advanced)

These are **JVM system properties** on the Gateway process (for example via `JAVA_OPTS`). They are not fields in the policy UI or in `gravitee.yml`.

| System property | Default | Description |
| --- | --- | --- |
| `gravitee.aws-lambda.client-cache.size` | `128` | Maximum number of cached `LambdaAsyncClient` instances (keyed by policy AWS settings). |
| `gravitee.aws-lambda.blocking-pool.size` | `max(4, available processors)` | Size of the dedicated worker pool used for AWS client setup and synchronous credential work (for example STS AssumeRole refresh) so it does not run on the Vert.x event loop. |


JVM options
```bash
JAVA_OPTS="-Dgravitee.aws-lambda.blocking-pool.size=32 -Dgravitee.aws-lambda.client-cache.size=256"
```



### Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| AWS Access Key<br>`accessKey`| string|  | | AWS Access Key|
| API Call Attempt Timeout (ms)<br>`apiCallAttemptTimeoutMs`| integer<br>`[0, +Inf]`|  | | The amount of time to wait (in milliseconds) for a single HTTP attempt to complete before giving up. If not set, the default value of the AWS SDK is used (disabled).|
| API Call Timeout (ms)<br>`apiCallTimeoutMs`| integer<br>`[0, +Inf]`|  | | The total amount of time (in milliseconds) for the client to complete the execution of an API call, including all retries. If not set, the default value of the AWS SDK is used (disabled).|
| Connection Timeout (ms)<br>`connectionTimeoutMs`| integer<br>`[0, +Inf]`|  | | The amount of time to wait (in milliseconds) when initially establishing a connection before giving up. If not set, the default value of the AWS SDK is used (2000ms).|
| AWS Lambda Function<br>`function`| string| ✅| | AWS Lambda function to call|
| Invocation Type<br>`invocationType`| enum (string)| ✅| `RequestResponse`| RequestResponse (default) – Invoke the function synchronously. Keep the connection open until the function returns a response or times out. The API response includes the function response and additional data.
Event – Invoke the function asynchronously. Send events that fail multiple times to the function's dead-letter queue (if one is configured). The API response only includes a status code.
DryRun – Validate parameter values and verify that the user or role has permission to invoke the function.<br>Values: `RequestResponse` `Event` `DryRun`|
| Log Type<br>`logType`| enum (string)| ✅| `None`| Set to Tail to include the execution log in the response. Applies to synchronously invoked functions only.<br>Values: `None` `Tail`|
| Lambda request payload<br>`payload`| string|  | | |
| Qualifier<br>`qualifier`| string|  | | Specify a version or alias to invoke a published version of the function.|
| Read Timeout (ms)<br>`readTimeoutMs`| integer<br>`[0, +Inf]`|  | | The amount of time to wait (in milliseconds) for data to be transferred over an established connection before the connection times out. If not set, the default value of the AWS SDK is used (30000ms).|
| AWS Region<br>`region`| string| ✅| `us-east-1`| AWS Region|
| Assume Role ARN<br>`roleArn`| string|  | | The arn of the role to be assumed. This is used when authentication is relying on the AWS Security Token Service (STS) to assume a Role and create temporary, short-lived sessions to use for authentication.|
| Role Session Name<br>`roleSessionName`| string|  | `gravitee`| An identifier for the assumed role session (Only used when authentication is based on AWS Security Token Service (STS)|
| AWS Secret Key<br>`secretKey`| string|  | | AWS Secret Key|
| Send lambda function result to consumer<br>`sendToConsumer`| boolean|  | | Check this option if you want to send the response of the lambda to the initial consumer without going to the final upstream (endpoints) selected by the gateway.|
| SSL / TLS Configuration<br>`ssl`| object|  | | Custom SSL/TLS trust configuration for the AWS SDK HTTP clients. When not configured, the JVM default truststore is used.<br/>See "SSL / TLS Configuration" section.|
| Context variables<br>`variables`| array|  | | <br/>See "Context variables" section.|


#### SSL / TLS Configuration (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Trust All Certificates<br>`trustAll`| boolean|  | | Skip certificate validation entirely. Use only for debugging purposes.|
| Truststore PEM Content<br>`trustStoreContent`| string|  | | Inline PEM-encoded certificate(s). Alternative to trustStorePath when using PEM type.|
| Truststore Password<br>`trustStorePassword`| string|  | | Password for the truststore (not applicable for PEM).|
| Truststore Path<br>`trustStorePath`| string|  | | File path to the truststore on the gateway filesystem.|
| Truststore Type<br>`trustStoreType`| enum (string)|  | | Type of the truststore (JKS, PKCS12, or PEM).<br>Values: `JKS` `PKCS12` `PEM`|


#### Context variables (Array)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Name<br>`name`| string|  | | |
| Value<br>`value`| string|  | `{#jsonPath(#lambdaResponse.content, '$.field')}`| |




## Examples

*Proxy API on Request phase*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "AWS Lambda example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "AWS Lambda",
            "enabled": true,
            "policy": "aws-lambda",
            "configuration":
              {
                  "scope": "REQUEST",
                  "region": "us-east-1",
                  "accessKey": "accessKey",
                  "secretKey": "secretKey",
                  "function": "lambda-example",
                  "payload": "{ \"key\": \"value\" }",
                  "invocationType": "RequestResponse",
                  "roleSessionName": "gravitee",
                  "logType": "None",
                  "variables": [
                      {
                          "name": "lambdaResponse",
                          "value": "{#jsonPath(#lambdaResponse.content, '$')}"
                      }
                  ],
                  "sendToConsumer": false
              }
          }
        ]
      }
    ]
  }
}

```
*Proxy API on Request phase with secrets*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "AWS Lambda example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "AWS Lambda",
            "enabled": true,
            "policy": "aws-lambda",
            "configuration":
              {
                  "scope": "REQUEST",
                  "region": "us-east-1",
                  "accessKey": "{#secrets.get('/vault/secret/aws:accessKey')}",
                  "secretKey": "{#secrets.get('/vault/secret/aws:secretKey')}",
                  "roleArn": "{#secrets.get('/vault/secret/aws:roleArn')}",
                  "function": "lambda-example",
                  "payload": "{ \"key\": \"value\" }",
                  "invocationType": "RequestResponse",
                  "roleSessionName": "gravitee",
                  "logType": "None",
                  "variables": [
                      {
                          "name": "lambdaResponse",
                          "value": "{#jsonPath(#lambdaResponse.content, '$')}"
                      }
                  ],
                  "sendToConsumer": false
              }
          }
        ]
      }
    ]
  }
}

```
*Message API CRD*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "aws-lambda-message-api-crd"
spec:
    name: "AWS Lambda example"
    type: "MESSAGE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        request:
          - name: "AWS Lambda"
            enabled: true
            policy: "aws-lambda"
            configuration:
              accessKey: accessKey
              function: lambda-example
              invocationType: RequestResponse
              logType: None
              payload: '{ "key": "value" }'
              region: us-east-1
              roleSessionName: gravitee
              scope: REQUEST
              secretKey: secretKey
              sendToConsumer: false
              variables:
                  - name: lambdaResponse
                    value: '{#jsonPath(#lambdaResponse.content, ''$'')}'

```


## Changelog

#### [3.4.2](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.4.1...3.4.2) (2026-09-30)


##### Bug Fixes

* aws lambda event loop ([48fed8e](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/48fed8e76b5cafe930b709c16fcd1b652821b49e))

#### [3.4.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.4.0...3.4.1) (2026-07-16)


##### Bug Fixes

* **aws-policy:** blocked thread warnings ([06135e8](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/06135e8c2baa0689756b9bc4b22e3ae8705f965f))

### [3.4.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.3.0...3.4.0) (2026-05-08)


##### Features

* enable policy for mcp proxy on request phase ([be78ea6](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/be78ea66da34b209661ea5b4d04993b18c965db6))

### [3.3.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.2.0...3.3.0) (2026-03-19)


##### Features

* add SSL/TLS configuration for AWS SDK HTTP clients ([390e6c2](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/390e6c27775521659f1c040ec8447299a33d64eb))

### [3.2.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.1.0...3.2.0) (2026-03-18)


##### Features

* add configurable aws lambda timeout ([b88427a](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/b88427a703f36d65ac8e3f44d7b67d4c38d97787))

### [3.1.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.0.0...3.1.0) (2025-12-10)


##### Features

* secret support for fields ([4240108](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/42401083c6a3078eeaa38447408fafb97c6203e0))

### [3.0.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/2.0.0...3.0.0) (2025-06-03)


##### chore

* **deps:** bump gravitee-apim to 4.8.0-SNAPSHOT ([60c8b1f](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/60c8b1f49b51ee825f9bfc6214504f300e44f43c))


##### BREAKING CHANGES

* **deps:** require at least APIM 4.8

### [3.0.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/2.0.0...3.0.0-alpha.1) (2025-04-22)


##### chore

* **deps:** bump gravitee-apim to 4.8.0-SNAPSHOT ([81b6a37](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/81b6a3750791b0cfbce8082d3c68160e31448a47))


##### BREAKING CHANGES

* **deps:** require at least APIM 4.8

### [2.0.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.3.1...2.0.0) (2025-03-10)


##### chore

* bump aws java sdk to 2.30.26 ([ad64c97](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/ad64c9790252157f9f16e40a4a97d3408086fde4))
* use gravitee-apim-bom in aws lambda policy ([c3ce44a](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/c3ce44a84a738d6c2e3528186d70361bf78bc706))


##### Features

* add task file to build and copy the plugin easily on APIM ([83961f8](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/83961f89a4743c9de47f0e7dc8556077a888ff5f))
* hide scope in policy studio v4 ([c861d41](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/c861d41d525816a191efeff7217d2f984a80e46c))
* implement aws lambda policy for V4 APIs ([0ef1925](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/0ef1925201d0e18d0b361ed671ceb8201aa0e1bc))


##### BREAKING CHANGES

* implementation has changed and use sdk v2 now

(cherry picked from commit 9783474d1b01007e931a9bc1cbf02b3943615420)
* Updating this sdk changes all the implementation of the policy

(cherry picked from commit 73435a3a69c7ca270c6076fcac5ab3e6f24d9ce1)
* require at least APIM 4.7

(cherry picked from commit ce8ce2e98d3894e5f6a64a7a3679a76a20043f40)

#### [1.3.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.3.0...1.3.1) (2025-03-10)


##### Bug Fixes

* revert pr 109 ([37078eb](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/37078ebfdf9c6206cb326ab9e564030bc8118ca2))

### [1.3.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.2.0...1.3.0) (2025-03-10)


##### Features

* add task file to build and copy the plugin easily on APIM ([90ec926](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/90ec926b7941241ffd698a1405911ad35517cb9c))
* hide scope in policy studio v4 ([a4b704e](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/a4b704eb2319f2a68369c8a3abb1f9dc871a78fe))
* implement aws lambda policy for V4 APIs ([9783474](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/9783474d1b01007e931a9bc1cbf02b3943615420))

### [1.2.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.1.2...1.2.0) (2024-06-25)


##### Bug Fixes

* **aws-lambda:** bump gravitee version ([cbb98ae](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/cbb98aee04673dad69f4e6092a95cb6cf2573999))


##### Features

* **aws-lambda:** Add support for STS to manage dynamic authentication credentials ([eaf7eb1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/eaf7eb11172de031bf202467ce26ec6c3a708da9))

#### [1.1.2](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.1.1...1.1.2) (2023-07-21)


##### Bug Fixes

* improve README ([7ce55a2](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/7ce55a2ac591adce63317f2cbfbc95e9224964dc))

#### [1.1.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.1.0...1.1.1) (2023-07-20)


##### Bug Fixes

* update policy description ([33ce709](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/33ce709bae20cdea3e5d87906f3cc007fd2940f2))

### [[secure]](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.0.0...[secure]) (2022-01-21)


##### Features

* **headers:** Internal rework and introduce HTTP Headers API ([e569a8d](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/e569a8da2713651d5d311aa0c6f206f64d1955d4)), closes [gravitee-io/issues#6772](https://github.com/gravitee-io/issues/issues/6772)
* **perf:** adapt policy for new classloader system ([7d96d3d](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/7d96d3d5d55dff41eb9634efb2c1035fe8620478)), closes [gravitee-io/issues#6758](https://github.com/gravitee-io/issues/issues/6758)

