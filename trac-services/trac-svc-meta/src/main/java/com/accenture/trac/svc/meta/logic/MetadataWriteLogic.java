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

package com.accenture.trac.svc.meta.logic;

import com.accenture.trac.common.api.meta.TagUpdate;
import com.accenture.trac.common.metadata.*;
import com.accenture.trac.svc.meta.dal.IMetadataDal;
import com.accenture.trac.svc.meta.validation.MetadataValidator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.accenture.trac.svc.meta.logic.MetadataConstants.OBJECT_FIRST_VERSION;
import static com.accenture.trac.svc.meta.logic.MetadataConstants.PUBLIC_API;


public class MetadataWriteLogic {

    private final IMetadataDal dal;

    public MetadataWriteLogic(IMetadataDal dal) {
        this.dal = dal;
    }

    public CompletableFuture<TagHeader> createObject(
            String tenant, ObjectType objectType,
            ObjectDefinition definition,
            Map<String, TagUpdate> attrUpdates,
            boolean apiTrust) {

        var validator = new MetadataValidator();
        var objectId = new Wrapper<UUID>();

        return CompletableFuture.completedFuture(validator)

                // Validation
//                .thenApply(v -> v.headerIsNull(definition))
//                .thenApply(v -> v.definitionMatchesType(definition, objectType))
//                .thenApply(v -> v.tagVersionIsBlank(tag))
//                .thenApply(v -> v.tagAttributesAreValid(tag))
                .thenApply(MetadataValidator::checkAndThrow)

                // Tag permissions
                //.thenApply(v -> checkReservedTagAttributes(tag, v, apiTrust))

                // Build object to save
                .thenApply(_x -> objectId.value = UUID.randomUUID())
                //.thenApply(_x -> setObjectHeader(definition, objectType, objectId.value, OBJECT_FIRST_VERSION))

                .thenApply(_x -> TagHeader.newBuilder().build());

//                .thenApply(defToSave -> tag.toBuilder()
//                        .setTagVersion(MetadataConstants.TAG_FIRST_VERSION)
//                        .setDefinition(defToSave).build())
//
//                // Save and return
//                .thenCompose(tagToSave -> dal.saveNewObject(tenant, tagToSave))
//                .thenApply(_ok -> objectId.value);
    }

    public CompletableFuture<TagHeader> updateObject(
            String tenant, ObjectType objectType,
            TagSelector priorVersion,
            ObjectDefinition definition,
            Map<String, TagUpdate> attrUpdates,
            boolean apiTrust) {

        var validator = new MetadataValidator();

        var priorSelector = TagSelector.newBuilder().build();
        var priorObjectId = MetadataCodec.decode(priorSelector.getObjectId());
        var priorTag = new Wrapper<Tag>();

        return CompletableFuture.completedFuture(validator)

                // Check whether versioning is supported for this object type
                // If not, we want to raise an error without reporting any other validation issues
                .thenApply(v -> v.typeSupportsVersioning(objectType))
                .thenApply(MetadataValidator::checkAndThrow)

                // Validate incoming object / tag
//                .thenApply(v -> v.headerIsValid(definition))
//                .thenApply(v -> v.headerMatchesType(definition, objectType))
//                .thenApply(v -> v.definitionMatchesType(definition, objectType))
//                .thenApply(v -> v.tagVersionIsBlank(tag))
//                .thenApply(v -> v.tagAttributesAreValid(tag))
                .thenApply(MetadataValidator::checkAndThrow)

                // Tag permissions
                //.thenApply(v -> checkReservedTagAttributes(tag, v, apiTrust))

                // Load prior object version
                .thenCompose(prior -> dal.loadLatestTag(tenant,
                        priorSelector.getObjectType(),
                        priorObjectId,
                        priorSelector.getObjectVersion()))

                .thenAccept(pt -> priorTag.value = pt)

                // TODO: Validate version increment
                .thenApply(pt -> validator)
                .thenApply(MetadataValidator::checkAndThrow)

                .thenApply(x_ -> TagHeader.newBuilder().build());

//                // Build tag to save
//                .thenApply(v -> objectVersion.value = priorTag.value.getDefinition().getHeader().getObjectVersion() + 1)
//                .thenApply(v -> bumpObjectVersion(definition, priorTag.value.getDefinition()))
//
//                .thenApply(defToSave -> tag.toBuilder()
//                        .setTagVersion(MetadataConstants.TAG_FIRST_VERSION)
//                        .setDefinition(defToSave).build())
//
//                // Save and return
//                .thenCompose(tagToSave -> dal.saveNewVersion(tenant, tagToSave))
//                .thenApply(_ok -> objectVersion.value);
    }

    public CompletableFuture<TagHeader> updateTag(
            String tenant, ObjectType objectType,
            TagSelector priorVersion,
            Map<String, TagUpdate> attrUpdates,
            boolean apiTrust) {

        var validator = new MetadataValidator();

        var priorSelector = TagSelector.newBuilder().build();
        var priorObjectId = MetadataCodec.decode(priorSelector.getObjectId());
        var priorTag = new Wrapper<Tag>();



        return CompletableFuture.completedFuture(validator)

                // Validate incoming object / tag
//                .thenApply(v -> v.headerIsValid(definition))
//                .thenApply(v -> v.headerMatchesType(definition, objectType))
//                // TODO: Allow a null definition when creating a new tag
//                .thenApply(v -> v.definitionMatchesType(definition, objectType))
//                .thenApply(v -> v.tagVersionIsValid(tag))
//                .thenApply(v -> v.tagAttributesAreValid(tag))
                .thenApply(MetadataValidator::checkAndThrow)

                // Tag permissions
                //.thenApply(v -> checkReservedTagAttributes(tag, v, apiTrust))

                // Load prior tag version
                //.thenApply(v -> specifierFromTag(tag))
                .thenCompose(prior -> dal.loadTag(tenant,
                        priorSelector.getObjectType(),
                        priorObjectId,
                        priorSelector.getObjectVersion(),
                        priorSelector.getTagVersion()))

                .thenAccept(pt -> priorTag.value = pt)

                // TODO: Validate increment

                .thenApply(x -> TagHeader.newBuilder().build());

//                // Build tag to save
//                .thenApply(pt -> tagVersion.value = priorTag.value.getTagVersion() + 1)
//                .thenApply(tv -> definition.toBuilder().clearDefinition().build())
//
//                .thenApply(defToSave -> tag.toBuilder()
//                        .setTagVersion(tagVersion.value)
//                        .setDefinition(defToSave).build())
//
//                // Save and return
//                .thenCompose(tagToSave -> dal.saveNewTag(tenant, tagToSave))
//                .thenApply(_ok -> tagVersion.value);
    }

    public CompletableFuture<TagHeader> preallocateId(String tenant, ObjectType objectType) {

        // New random ID
        var objectId = UUID.randomUUID();

        // Save as a preallocated ID in the DAL
        return dal.preallocateObjectId(tenant, objectType, objectId)

                .thenApply(x -> TagHeader.newBuilder().build());

//                // Return an object header with the new ID
//                .thenApply(_ok -> ObjectHeader.newBuilder()
//                .setObjectType(objectType)
//                .setObjectId(MetadataCodec.encode(objectId))
//                .setObjectVersion(OBJECT_FIRST_VERSION)
//                .build());
    }

    public CompletableFuture<TagHeader> createPreallocatedObject(
            String tenant, ObjectType objectType,
            ObjectDefinition definition,
            Map<String, TagUpdate> attrUpdates) {

        var validator = new MetadataValidator();

        return CompletableFuture.completedFuture(validator)

                // Validation
//                .thenApply(v -> v.headerIsValid(definition))
//                .thenApply(v -> v.headerMatchesType(definition, objectType))
//                .thenApply(v -> v.headerIsOnFirstVersion(definition))
//                .thenApply(v -> v.definitionMatchesType(definition, objectType))
//                .thenApply(v -> v.tagVersionIsBlank(tag))
//                .thenApply(v -> v.tagAttributesAreValid(tag))
                .thenApply(MetadataValidator::checkAndThrow)

                // Preallocated objects are always on the trusted API
                // So no need to check reserved tag attributes

                .thenApply(x -> TagHeader.newBuilder().build());

//                // Set tag version if not already set
//                .thenApply(defToSave -> tag.toBuilder()
//                        .setTagVersion(MetadataConstants.TAG_FIRST_VERSION)
//                        .build())
//
//                // Save and return
//                .thenCompose(tagToSave -> dal.savePreallocatedObject(tenant, tagToSave))
//
//                // Return object header on success
//                .thenApply(_ok -> definition.getHeader());
    }

    private MetadataValidator checkReservedTagAttributes(Tag tag, MetadataValidator validator, boolean apiTrust) {

        if (apiTrust == PUBLIC_API)

            return validator
                    .tagAttributesAreNotReserved(tag)
                    .checkAndThrowPermissions();

        else  // trust = TRUSTED_API

            return validator;
    }

//    private ObjectDefinition setObjectHeader(ObjectDefinition definition, ObjectType objectType, UUID objectId, int objectVersion) {
//
//        var header = ObjectHeader.newBuilder()
//                .setObjectType(objectType)
//                .setObjectId(MetadataCodec.encode(objectId))
//                .setObjectVersion(objectVersion);
//
//        return definition.toBuilder()
//                .setHeader(header)
//                .build();
//    }

//    private ObjectDefinition bumpObjectVersion(ObjectDefinition newDefinition, ObjectDefinition priorDefinition) {
//
//        var priorHeader = priorDefinition.getHeader();
//
//        return newDefinition.toBuilder()
//                .setHeader(priorHeader.toBuilder()
//                .setObjectVersion(priorHeader.getObjectVersion() + 1))
//                .build();
//    }

//    private ObjectSpecifier specifierFromTag(Tag tag) {
//
//        var header = tag.getDefinition().getHeader();
//
//        var spec = new ObjectSpecifier();
//        spec.objectType = header.getObjectType();
//        spec.objectId = MetadataCodec.decode(header.getObjectId());
//        spec.objectVersion = header.getObjectVersion();
//        spec.tagVersion = tag.getTagVersion();
//
//        return spec;
//    }
//
//    private static class ObjectSpecifier {
//        ObjectType objectType;
//        UUID objectId;
//        int objectVersion;
//        int tagVersion;
//    }

    private static class Wrapper<T> {

        public T value;
    }
}
