/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
//package co.elastic.apm.agent.impl.cloud;
//
//public enum CloudMetadata {
//
//    /** AWS Example:
//     * {
//     * "accountId": "946960629917",
//     * "architecture": "x86_64",
//     * "availabilityZone": "us-east-2a",
//     * "billingProducts": null,
//     * "devpayProductCodes": null,
//     * "marketplaceProductCodes": null,
//     * "imageId": "ami-07c1207a9d40bc3bd",
//     * "instanceId": "i-0ae894a7c1c4f2a75",
//     * "instanceType": "t2.medium",
//     * "kernelId": null,
//     * "pendingTime": "2020-06-12T17:46:09Z",
//     * "privateIp": "172.31.0.212",
//     * "ramdiskId": null,
//     * "region": "us-east-2",
//     * "version": "2017-09-30"
//     * }
//     */
//    AWS("aws", "accountId", ""),
//
//    /**
//     *
//     */
//    GCP,
//    AZURE,
//    NONE;
//
//    private final String provider;
//    private final String accountIdFieldName;
//    private final String instanceIdFieldName;
//    private final String instanceTypeFieldName;
//    private final String availabilityZoneFieldName;
//    private final String regionFieldName;
//
//    CloudMetadata(String provider,
//                  String accountIdFieldName,
//                  String instanceIdFieldName,
//                  String instanceTypeFieldName,
//                  String availabilityZoneFieldName,
//                  String regionFieldName) {
//        this.provider = provider;
//        this.accountIdFieldName = accountIdFieldName;
//        this.instanceIdFieldName = instanceIdFieldName;
//        this.instanceTypeFieldName = instanceTypeFieldName;
//        this.availabilityZoneFieldName = availabilityZoneFieldName;
//        this.regionFieldName = regionFieldName;
//    }
//
//    public String getProvider() {
//        return provider;
//    }
//
//    public String getAccountIdFieldName() {
//        return accountIdFieldName;
//    }
//
//    public String getInstanceIdFieldName() {
//        return instanceIdFieldName;
//    }
//
//    public String getInstanceTypeFieldName() {
//        return instanceTypeFieldName;
//    }
//
//    public String getAvailabilityZoneFieldName() {
//        return availabilityZoneFieldName;
//    }
//
//    public String getRegionFieldName() {
//        return regionFieldName;
//    }
//}
