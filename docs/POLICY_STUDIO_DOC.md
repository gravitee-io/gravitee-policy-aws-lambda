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


