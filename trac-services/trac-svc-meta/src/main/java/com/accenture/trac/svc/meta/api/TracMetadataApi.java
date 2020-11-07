/*
 * Copyright 2020 Accenture Global Solutions Limited
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

package com.accenture.trac.svc.meta.api;

import com.accenture.trac.common.api.*;
import com.accenture.trac.metadata.Tag;
import com.accenture.trac.metadata.TagHeader;
import com.accenture.trac.svc.meta.services.MetadataReadService;
import com.accenture.trac.svc.meta.services.MetadataSearchService;
import com.accenture.trac.svc.meta.services.MetadataWriteService;
import io.grpc.stub.StreamObserver;

import static com.accenture.trac.svc.meta.services.MetadataConstants.PUBLIC_API;


public class TracMetadataApi extends TracMetadataApiGrpc.TracMetadataApiImplBase {

    private final BaseMetadataApi baseApi;

    public TracMetadataApi(
            MetadataReadService readService,
            MetadataWriteService writeService,
            MetadataSearchService searchService) {

        baseApi = new BaseMetadataApi(readService, writeService, searchService, PUBLIC_API);
    }

    @Override
    public void createObject(MetadataWriteRequest request, StreamObserver<TagHeader> responseObserver) {
        baseApi.createObject(request, responseObserver);
    }

    @Override
    public void updateObject(MetadataWriteRequest request, StreamObserver<TagHeader> responseObserver) {
        baseApi.updateObject(request, responseObserver);
    }

    @Override
    public void updateTag(MetadataWriteRequest request, StreamObserver<TagHeader> responseObserver) {
        baseApi.updateTag(request, responseObserver);
    }

    @Override
    public void readObject(MetadataReadRequest request, StreamObserver<Tag> responseObserver) {
        baseApi.readObject(request, responseObserver);
    }

    @Override
    public void readBatch(MetadataBatchRequest request, StreamObserver<MetadataBatchResponse> responseObserver) {
        baseApi.readBatch(request, responseObserver);
    }

    @Override
    public void search(MetadataSearchRequest request, StreamObserver<MetadataSearchResponse> responseObserver) {
        baseApi.search(request, responseObserver);
    }

    @Override
    public void getObject(MetadataGetRequest request, StreamObserver<Tag> responseObserver) {
        baseApi.getObject(request, responseObserver);
    }

    @Override
    public void getLatestObject(MetadataGetRequest request, StreamObserver<Tag> responseObserver) {
        baseApi.getLatestObject(request, responseObserver);
    }

    @Override
    public void getLatestTag(MetadataGetRequest request, StreamObserver<Tag> responseObserver) {
        baseApi.getLatestTag(request, responseObserver);
    }
}
