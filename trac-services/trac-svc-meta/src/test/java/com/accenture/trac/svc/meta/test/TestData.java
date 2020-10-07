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

package com.accenture.trac.svc.meta.test;

import com.accenture.trac.common.metadata.*;
import com.google.protobuf.ByteString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public class TestData {

    public static final boolean INCLUDE_HEADER = true;
    public static final boolean NO_HEADER = false;

    public static final boolean UPDATE_HEADER = true;
    public static final boolean KEEP_ORIGINAL_HEADER = false;

    public static final boolean UPDATE_TAG_VERSION = true;
    public static final boolean KEEP_ORIGINAL_TAG_VERSION = false;

    public static final String TEST_TENANT = "ACME_CORP";


    public static ObjectDefinition dummyDefinitionForType(ObjectType objectType, boolean includeHeader) {

        switch (objectType) {

            case DATA: return dummyDataDef(includeHeader);
            case MODEL: return dummyModelDef(includeHeader);
            case FLOW: return dummyFlowDef(includeHeader);
            case JOB: return dummyJobDef(includeHeader);
            case FILE: return dummyFileDef(includeHeader);
            case CUSTOM: return dummyCustomDef(includeHeader);

            default:
                throw new RuntimeException("No dummy data available for object type " + objectType.name());
        }
    }

    public static Tag dummyTagForObjectType(ObjectType objectType) {

        return dummyTag(dummyDefinitionForType(objectType, INCLUDE_HEADER));
    }

    public static ObjectDefinition dummyVersionForType(ObjectDefinition definition, boolean updateHeader) {

        // Not all object types have semantics defined for versioning
        // It is sometimes helpful to create versions anyway for testing
        // E.g. to test that version increments are rejected for objects that don't support versioning!

        var objectType = definition.getHeader().getObjectType();

        switch (objectType) {

            case DATA: return nextDataDef(definition, updateHeader);
            case MODEL: return nextModelDef(definition, updateHeader);
            case CUSTOM: return nextCustomDef(definition, updateHeader);

            case FLOW:
            case JOB:
            case FILE:
                return definition;

            default:
                throw new RuntimeException("No second version available in dummy data for object type " + objectType.name());
        }
    }

    public static ObjectDefinition newObjectHeader(ObjectType objectType, ObjectDefinition.Builder def, boolean includeHeader) {

        if (includeHeader == NO_HEADER)
            return def.build();

        else
            return def
                .setHeader(ObjectHeader.newBuilder()
                .setObjectType(objectType)
                .setObjectId(MetadataCodec.encode(UUID.randomUUID()))
                .setObjectVersion(1))
                .build();
    }

    public static ObjectDefinition newVersionHeader(ObjectDefinition.Builder defUpdate, boolean updateHeader) {

        if (updateHeader == KEEP_ORIGINAL_HEADER)
            return defUpdate.build();

        else
            return defUpdate
                .setHeader(defUpdate.getHeader()
                .toBuilder()
                .setObjectVersion(defUpdate.getHeader().getObjectVersion() + 1)
                .build())
                .build();
    }

    public static ObjectDefinition dummyDataDef(boolean includeHeader) {

        var def = ObjectDefinition.newBuilder()
            .setData(DataDefinition.newBuilder()
            .addStorage("test-storage")
            .setPath("path/to/test/dataset")
            .setFormat(DataFormat.CSV)
            .setSchema(TableDefinition.newBuilder()
                .addField(FieldDefinition.newBuilder()
                        .setFieldName("transaction_id")
                        .setFieldType(BasicType.STRING)
                        .setFieldOrder(1)
                        .setBusinessKey(true))
                .addField(FieldDefinition.newBuilder()
                        .setFieldName("customer_id")
                        .setFieldType(BasicType.STRING)
                        .setFieldOrder(2)
                        .setBusinessKey(true))
                .addField(FieldDefinition.newBuilder()
                        .setFieldName("order_date")
                        .setFieldType(BasicType.DATE)
                        .setFieldOrder(3)
                        .setBusinessKey(true))
                .addField(FieldDefinition.newBuilder()
                        .setFieldName("widgets_ordered")
                        .setFieldType(BasicType.INTEGER)
                        .setFieldOrder(4)
                        .setBusinessKey(true))));

        return newObjectHeader(ObjectType.DATA, def, includeHeader);
    }

    public static ObjectDefinition nextDataDef(ObjectDefinition origDef, boolean updateHeader) {

        if (origDef.getHeader().getObjectType() != ObjectType.DATA || !origDef.hasData())
            throw new RuntimeException("Original object is not a valid data definition");

        var defUpdate = origDef.toBuilder()
                .setData(origDef.getData()
                .toBuilder()
                .setSchema(origDef.getData().getSchema().toBuilder()
                    .addField(FieldDefinition.newBuilder()
                    .setFieldName("extra_field")
                    .setFieldOrder(origDef.getData().getSchema().getFieldCount())
                    .setFieldType(BasicType.FLOAT)
                    .setFieldLabel("We got an extra field!")
                    .setFormatCode("PERCENT")
                    .build()).build()));

        return newVersionHeader(defUpdate, updateHeader);
    }

    public static ObjectDefinition dummyModelDef(boolean includeHeader) {

        var def = ObjectDefinition.newBuilder()
                .setModel(ModelDefinition.newBuilder()
                .setLanguage("python")
                .setRepository("trac-test-repo")
                .setRepositoryVersion("trac-test-repo-1.2.3-RC4")
                .setPath("src/main/python")
                .setEntryPoint("trac_test.test1.SampleModel1")
                .putParam("param1", ModelParameter.newBuilder().setParamType(TypeSystem.descriptor(BasicType.STRING)).build())
                .putParam("param2", ModelParameter.newBuilder().setParamType(TypeSystem.descriptor(BasicType.INTEGER)).build())
                .putInput("input1", TableDefinition.newBuilder()
                        .addField(FieldDefinition.newBuilder()
                                .setFieldName("field1")
                                .setFieldType(BasicType.DATE))
                        .addField(FieldDefinition.newBuilder()
                                .setFieldName("field2")
                                .setBusinessKey(true)
                                .setFieldType(BasicType.DECIMAL)
                                .setFieldLabel("A display name")
                                .setCategorical(true)
                                .setFormatCode("GBP"))
                        .build())
                .putOutput("output1", TableDefinition.newBuilder()
                        .addField(FieldDefinition.newBuilder()
                                .setFieldName("checksum_field")
                                .setFieldType(BasicType.DECIMAL))
                        .build()));

        return newObjectHeader(ObjectType.MODEL, def, includeHeader);
    }

    public static ObjectDefinition nextModelDef(ObjectDefinition origDef, boolean updateHeader) {

        if (origDef.getHeader().getObjectType() != ObjectType.MODEL || !origDef.hasModel())
            throw new RuntimeException("Original object is not a valid model definition");

        var defUpdate = origDef.toBuilder()
                .setModel(origDef.getModel()
                .toBuilder()
                .putParam("param3", ModelParameter.newBuilder().setParamType(TypeSystem.descriptor(BasicType.DATE)).build()));

        return newVersionHeader(defUpdate, updateHeader);
    }

    public static ObjectDefinition dummyFlowDef(boolean includeHeader) {

        var def = ObjectDefinition.newBuilder()
                .setFlow(FlowDefinition.newBuilder()
                .putNode("input_1", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNode("main_model", FlowNode.newBuilder().setNodeType(FlowNodeType.MODEL_NODE).build())
                .putNode("output_1", FlowNode.newBuilder().setNodeType(FlowNodeType.OUTPUT_NODE).build())
                .addEdge(FlowEdge.newBuilder()
                        .setHead(FlowSocket.newBuilder().setNode("main_model").setSocket("input_1"))
                        .setTail(FlowSocket.newBuilder().setNode("input_1")))
                .addEdge(FlowEdge.newBuilder()
                        .setHead(FlowSocket.newBuilder().setNode("output_1"))
                        .setTail(FlowSocket.newBuilder().setNode("main_model").setSocket("output_1"))));

        return newObjectHeader(ObjectType.FLOW, def, includeHeader);
    }

    public static ObjectDefinition dummyJobDef(boolean includeHeader) {

        // Job will be invalid because the model ID it points to does not exist!
        // Ok for e.g. DAL testing, but will fail metadata validation

        var def = ObjectDefinition.newBuilder()
                .setJob(JobDefinition.newBuilder()
                .setJobType(JobType.RUN_MODEL)
                .setTargetId(MetadataCodec.encode(UUID.randomUUID())));

        return newObjectHeader(ObjectType.JOB, def, includeHeader);
    }

    public static ObjectDefinition dummyFileDef(boolean includeHeader) {

        var def = ObjectDefinition.newBuilder()
                .setFile(FileDefinition.newBuilder()
                .addStorage("test-storage")
                .setStoragePath("<preallocated_id>/contents/magic_template.xlsx")
                .setName("magic_template")
                .setExtension("docx")
                .setSize(45285)
                .setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .build());

        return newObjectHeader(ObjectType.FILE, def, includeHeader);
    }

    public static ObjectDefinition dummyCustomDef(boolean includeHeader) {

        var jsonReportDef = "{ reportType: 'magic', mainGraph: { content: 'more_magic' } }";

        var def = ObjectDefinition.newBuilder()
                .setCustom(CustomDefinition.newBuilder()
                .setCustomType("REPORT")
                .setCustomSchemaVersion(2)
                .setCustomData(ByteString.copyFromUtf8(jsonReportDef))
                .build());

        return newObjectHeader(ObjectType.CUSTOM, def, includeHeader);
    }

    public static ObjectDefinition nextCustomDef(ObjectDefinition origDef, boolean updateHeader) {

        if (origDef.getHeader().getObjectType() != ObjectType.CUSTOM || !origDef.hasCustom())
            throw new RuntimeException("Original object is not a valid custom definition");

        var ver = origDef.getHeader().getObjectVersion();
        var jsonReportDef = "{ reportType: 'magic', mainGraph: { content: 'more_magic_" + ver + " ' } }";

        var defUpdate = origDef.toBuilder()
                .setCustom(origDef.getCustom()
                .toBuilder()
                .setCustomData(ByteString.copyFromUtf8(jsonReportDef)));

        return newVersionHeader(defUpdate, updateHeader);
    }

    public static Tag dummyTag(ObjectDefinition definition) {

        return Tag.newBuilder()
                .setDefinition(definition)
                .setTagVersion(1)
                .putAttr("dataset_key", MetadataCodec.encodeValue("widget_orders"))
                .putAttr("widget_type", MetadataCodec.encodeValue("non_standard_widget"))
                .build();
    }

    public static Tag nextTag(Tag previous, boolean updateTagVersion) {

        var updatedTag = previous.toBuilder()
                .putAttr("extra_attr", Value.newBuilder()
                        .setType(TypeSystem.descriptor(BasicType.STRING))
                        .setStringValue("A new descriptive value")
                        .build());

        if (updateTagVersion == KEEP_ORIGINAL_TAG_VERSION)
            return updatedTag.build();

        else
            return updatedTag
                .setTagVersion(previous.getTagVersion() + 1)
                .build();
    }

    public static Tag addMultiValuedAttr(Tag tag) {

        var dataClassification = MetadataCodec.encodeArrayValue(
                List.of("pii", "bcbs239", "confidential"),
                TypeSystem.descriptor(BasicType.STRING));

        return tag.toBuilder()
                .putAttr("data_classification", dataClassification)
                .build();
    }

    public static <T> T unwrap(CompletableFuture<T> future) throws Exception {

        try {
            return future.get();
        }
        catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof Exception)
                throw (Exception) cause;
            throw e;
        }
    }


    // Create Java objects according to the TRAC type system

    public static Object objectOfType(BasicType basicType) {

        switch (basicType) {

            case BOOLEAN: return true;
            case INTEGER: return (long) 42;
            case FLOAT: return Math.PI;
            case DECIMAL: return new BigDecimal("1234.567");
            case STRING: return "the_droids_you_are_looking_for";
            case DATE: return LocalDate.now();

            // Metadata datetime attrs have microsecond precision
            case DATETIME:
                var dateTime = OffsetDateTime.now(ZoneOffset.UTC);
                return truncateMicrosecondPrecision(dateTime);

            default:
                throw new RuntimeException("Test object not available for basic type " + basicType.toString());
        }
    }

    public static Object objectOfDifferentType(BasicType basicType) {

        if (basicType == BasicType.STRING)
            return objectOfType(BasicType.INTEGER);
        else
            return objectOfType(BasicType.STRING);
    }

    public static Object differentObjectOfSameType(BasicType basicType, Object originalObject) {

        switch (basicType) {

            case BOOLEAN: return ! ((Boolean) originalObject);
            case INTEGER: return ((Long) originalObject) + 1L;
            case FLOAT: return ((Double) originalObject) * 2.0D;
            case DECIMAL: return ((BigDecimal) originalObject).multiply(new BigDecimal(2));
            case STRING: return originalObject.toString() + " and friends";
            case DATE: return ((LocalDate) originalObject).plusDays(1);
            case DATETIME: return ((OffsetDateTime) originalObject).plusHours(1);

            default:
                throw new RuntimeException("Test object not available for basic type " + basicType.toString());
        }
    }

    public static OffsetDateTime truncateMicrosecondPrecision(OffsetDateTime dateTime) {

        int precision = 6;

        var nanos = dateTime.getNano();
        var nanoPrecision = (int) Math.pow(10, 9 - precision);
        var truncatedNanos = (nanos / nanoPrecision) * nanoPrecision;
        return dateTime.withNano(truncatedNanos);
    }
}