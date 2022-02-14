/*
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
 */
package co.elastic.apm.agent.azurestorage;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

import java.net.URL;
import java.util.Map;

public class AzureStorageHelper {
    private final Tracer tracer;
    public static String SPAN_NAME = "AzureBlob";
    public static String SPAN_SUBTYPE = "azureblob";
    public static String SPAN_TYPE = "storage";

    public AzureStorageHelper(Tracer tracer) {
        this.tracer = tracer;
    }

    public Span startAzureStorageSpan(String method, URL url, Map<String, String> requestHeaderMap) {

        Span span = tracer.createExitChildSpan();
        if (span == null) {
            return null;
        }

        BlobUrl blobUrl = new BlobUrl(url);
        String action = null;

        String query = url.getQuery() != null ? url.getQuery().toLowerCase() : "";

        switch (method)
        {
            case "DELETE":
                action = "Delete";
                break;
            case "GET":
                if (query.indexOf("restype=container")!=-1)
                {
                    if (query.indexOf("comp=list")!=-1)
                        action = "ListBlobs";
                    else if (query.indexOf("comp=acl")!=-1)
                        action = "GetAcl";
                    else
                        action = "GetProperties";
                }
                else
                {
                    if (query.indexOf("comp=metadata")!=-1)
                        action = "GetMetadata";
                    else if (query.indexOf("comp=list")!=-1)
                        action = "ListContainers";
                    else if (query.indexOf("comp=tags")!=-1)
                        action = query.indexOf("where=")!=-1 ? "FindTags" : "GetTags";
                    else
                        action = "Download";
                }
                break;
            case "HEAD":
                if (query.indexOf("comp=metadata")!=-1)
                    action = "GetMetadata";
                else if (query.indexOf("comp=acl")!=-1)
                    action = "GetAcl";
                else
                    action = "GetProperties";
                break;
            case "POST":
                if (query.indexOf("comp=batch")!=-1)
                    action = "Batch";
                else if (query.indexOf("comp=query")!=-1)
                    action = "Query";
                break;
            case "PUT":
                String msCopySource = requestHeaderMap.get("x-ms-copy-source");
                String msBlobType = requestHeaderMap.get("x-ms-blob-type");
                if (msCopySource!=null && !"".equals(msCopySource))
                    action = "Copy";
                else if (query.indexOf("comp=copy")!=-1)
                    action = "Abort";
                else if ((msBlobType!=null &&!"".equals(msBlobType)) ||
                    query.indexOf("comp=block")!=-1 ||
                    query.indexOf("comp=blocklist")!=-1 ||
                    query.indexOf("comp=page")!=-1 ||
                    query.indexOf("comp=appendblock")!=-1)
                    action = "Upload";
                else if (query.indexOf("comp=metadata")!=-1)
                    action = "SetMetadata";
                else if (query.indexOf("comp=acl")!=-1)
                    action = "SetAcl";
                else if (query.indexOf("comp=properties")!=-1)
                    action = "SetProperties";
                else if (query.indexOf("comp=lease")!=-1)
                    action = "Lease";
                else if (query.indexOf("comp=snapshot")!=-1)
                    action = "Snapshot";
                else if (query.indexOf("comp=undelete")!=-1)
                    action = "Undelete";
                else if (query.indexOf("comp=tags")!=-1)
                    action = "SetTags";
                else if (query.indexOf("comp=tier")!=-1)
                    action = "SetTier";
                else if (query.indexOf("comp=expiry")!=-1)
                    action = "SetExpiry";
                else if (query.indexOf("comp=seal")!=-1)
                    action = "Seal";
                else
                    action = "Create";

                break;
        }

        if (action == null) return null;

        String name = SPAN_NAME + " " + action + " " + blobUrl.getResourceName();

        span.activate()
            .withAction(action)
            .withType(SPAN_TYPE)
            .withSubtype(SPAN_SUBTYPE)
            .withName(name, AbstractSpan.PRIO_DEFAULT - 1);

        span.appendToName(name);

        span.getContext()
            .getDestination()
            .withAddress(blobUrl.getFullyQualifiedNamespace())
            .withPort(blobUrl.getPort())
            .getService()
            .withType(SPAN_TYPE)
            .withResource(SPAN_SUBTYPE + "/" + blobUrl.getStorageAccountName())
            .withName(name);
        return span;
    }
}

