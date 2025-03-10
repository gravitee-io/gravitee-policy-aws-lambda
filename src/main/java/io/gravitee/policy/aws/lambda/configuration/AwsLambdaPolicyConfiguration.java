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

import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.LogType;
import io.gravitee.policy.api.PolicyConfiguration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AwsLambdaPolicyConfiguration implements PolicyConfiguration {

    private PolicyScope scope = PolicyScope.REQUEST;

    private String region = "us-east-1";

    private String accessKey, secretKey;

    private String function;

    private String payload;

    private InvocationType invocationType = InvocationType.RequestResponse;

    private String qualifier;

    private String roleArn;

    private String roleSessionName = "gravitee";

    private LogType logType = LogType.None;

    private List<Variable> variables = new ArrayList<>();

    private boolean sendToConsumer;

    public PolicyScope getScope() {
        return scope;
    }

    public void setScope(PolicyScope scope) {
        this.scope = scope;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> variables) {
        this.variables = variables;
    }

    public boolean isSendToConsumer() {
        return sendToConsumer;
    }

    public void setSendToConsumer(boolean sendToConsumer) {
        this.sendToConsumer = sendToConsumer;
    }

    public InvocationType getInvocationType() {
        return invocationType;
    }

    public void setInvocationType(InvocationType invocationType) {
        this.invocationType = invocationType;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public void setRoleArn(String roleArn) {
        this.roleArn = roleArn;
    }

    public String getRoleSessionName() {
        return roleSessionName;
    }

    public void setRoleSessionName(String roleSessionName) {
        this.roleSessionName = roleSessionName;
    }

    public LogType getLogType() {
        return logType;
    }

    public void setLogType(LogType logType) {
        this.logType = logType;
    }
}
