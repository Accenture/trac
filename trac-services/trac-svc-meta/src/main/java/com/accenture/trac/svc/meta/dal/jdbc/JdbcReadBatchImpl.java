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

package com.accenture.trac.svc.meta.dal.jdbc;

import com.accenture.trac.common.metadata.*;
import com.accenture.trac.svc.meta.dal.jdbc.dialects.IDialect;
import com.google.protobuf.InvalidProtocolBufferException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


class JdbcReadBatchImpl {

    private final IDialect dialect;
    private final AtomicInteger mappingStage;

    JdbcReadBatchImpl(IDialect dialect) {
        this.dialect = dialect;
        this.mappingStage = new AtomicInteger();
    }

    JdbcBaseDal.KeyedItems<ObjectType>
    readObjectTypeById(Connection conn, short tenantId, UUID[] objectId) throws SQLException {

        var mappingStage = insertIdForMapping(conn, objectId);
        mapObjectById(conn, tenantId, mappingStage);

        var query =
                "select object_pk, object_type\n" +
                "from object_id oid\n" +
                "join key_mapping km\n" +
                "  on oid.object_pk = km.pk\n" +
                "where oid.tenant_id = ?\n" +
                "  and km.mapping_stage = ?\n" +
                "order by km.ordering";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            try (var rs = stmt.executeQuery()) {

                var keys = new long[objectId.length];
                var types = new ObjectType[objectId.length];

                for (int i = 0; i < objectId.length; i++) {

                    if (!rs.next())
                        throw new JdbcException(JdbcErrorCode.NO_DATA);

                    var pk = rs.getLong(1);
                    var objectTypeCode = rs.getString(2);
                    var objectType = ObjectType.valueOf(objectTypeCode);

                    keys[i] = pk;
                    types[i] = objectType;
                }

                if (rs.next())
                    throw new JdbcException(JdbcErrorCode.TOO_MANY_ROWS);

                return new JdbcBaseDal.KeyedItems<>(keys, types);
            }
        }
    }

    JdbcBaseDal.KeyedItems<ObjectDefinition>
    readDefinitionByVersion(
            Connection conn, short tenantId,
            ObjectType[] objectType, long[] objectFk, int[] objectVersion)
            throws SQLException {

        var mappingStage = insertFkAndVersionForMapping(conn, objectFk, objectVersion);
        mapDefinitionByVersion(conn, tenantId, mappingStage);

        return fetchDefinition(conn, tenantId, objectType, mappingStage);
    }

    JdbcBaseDal.KeyedItems<ObjectDefinition>
    readDefinitionByLatest(
            Connection conn, short tenantId,
            ObjectType[] objectType, long[] objectFk)
            throws SQLException {

        var mappingStage = insertFkForMapping(conn, objectFk);
        mapDefinitionByLatest(conn, tenantId, mappingStage);

        return fetchDefinition(conn, tenantId, objectType, mappingStage);
    }

    private JdbcBaseDal.KeyedItems<ObjectDefinition>
    fetchDefinition(
            Connection conn, short tenantId,
            ObjectType[] objectType, int mappingStage)
            throws SQLException {

        var query =
                "select definition_pk, object_version, definition\n" +
                "from object_definition def\n" +
                "join key_mapping km\n" +
                "  on def.definition_pk = km.pk\n" +
                "where def.tenant_id = ?\n" +
                "  and km.mapping_stage = ?\n" +
                "order by km.ordering";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            try (var rs = stmt.executeQuery()) {

                long[] pks = new long[objectType.length];
                int[] versions = new int[objectType.length];
                ObjectDefinition[] defs = new ObjectDefinition[objectType.length];

                for (var i = 0; i < objectType.length; i++) {

                    if (!rs.next())
                        throw new JdbcException(JdbcErrorCode.NO_DATA);

                    var defPk = rs.getLong(1);
                    var defVersion = rs.getInt(2);
                    var defEncoded = rs.getBytes(3);
                    var defDecoded = ObjectDefinition.parseFrom(defEncoded);

                    // TODO: Encode / decode helper, type = protobuf | json ?

                    pks[i] = defPk;
                    versions[i] = defVersion;
                    defs[i] = defDecoded;
                }

                if (rs.next())
                    throw new JdbcException(JdbcErrorCode.TOO_MANY_ROWS);

                return new JdbcBaseDal.KeyedItems<>(pks, versions, defs);
            }
            catch (InvalidProtocolBufferException e) {
                throw new JdbcException(JdbcErrorCode.INVALID_OBJECT_DEFINITION);
            }

        }
    }

    JdbcBaseDal.KeyedItems<Tag.Builder>
    readTagByVersion(Connection conn, short tenantId, long[] definitionFk, int[] tagVersion) throws SQLException {

        var mappingStage = insertFkAndVersionForMapping(conn, definitionFk, tagVersion);
        mapTagByVersion(conn, tenantId, mappingStage);

        var headers = fetchTagHeader(conn, tenantId, definitionFk.length, mappingStage);
        var attrs = fetchTagAttrs(conn, tenantId, definitionFk.length, mappingStage);

        return applyTagAttrs(headers, attrs);
    }

    JdbcBaseDal.KeyedItems<Tag.Builder>
    readTagByLatest(Connection conn, short tenantId, long[] definitionFk) throws SQLException {

        var mappingStage = insertFkForMapping(conn, definitionFk);
        mapTagByLatest(conn, tenantId, mappingStage);

        var headers = fetchTagHeader(conn, tenantId, definitionFk.length, mappingStage);
        var attrs = fetchTagAttrs(conn, tenantId, definitionFk.length, mappingStage);

        return applyTagAttrs(headers, attrs);
    }

    JdbcBaseDal.KeyedItems<Tag.Builder>
    readTagByPk(Connection conn, short tenantId, long[] tagPk) throws SQLException {

        var mappingStage = insertPk(conn, tagPk);

        var headers = fetchTagHeader(conn, tenantId, tagPk.length, mappingStage);
        var attrs = fetchTagAttrs(conn, tenantId, tagPk.length, mappingStage);

        return applyTagAttrs(headers, attrs);
    }

    private JdbcBaseDal.KeyedItems<TagHeader>
    fetchTagHeader(Connection conn, short tenantId, int length, int mappingStage) throws SQLException {

        // Tag records contain no attributes, we only need pks and versions
        // Note: Common attributes may be added to the tag table as search optimisations, but do not need to be read

        var query =
                "select \n" +
                "   tag.tag_pk, tag.object_type, \n" +
                "   tag.object_id_hi, tag.object_id_lo, \n" +
                "   tag.object_version, tag.tag_version \n" +
                "from tag\n" +
                "join key_mapping km\n" +
                "  on tag.tag_pk = km.pk\n" +
                "where tag.tenant_id = ?\n" +
                "  and km.mapping_stage = ?\n" +
                "order by km.ordering";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            try (var rs = stmt.executeQuery()) {

                long[] pks = new long[length];
                int[] versions = new int[length];
                TagHeader[] tagHeaders = new TagHeader[length];

                for (var i = 0; i < length; i++) {

                    if (!rs.next())
                        throw new JdbcException(JdbcErrorCode.NO_DATA);

                    var tagPk = rs.getLong(1);
                    var objectType = rs.getString(2);
                    var objectIdHi = rs.getLong(3);
                    var objectIdLo = rs.getLong(4);
                    var objectVersion = rs.getInt(5);
                    var tagVersion = rs.getInt(6);

                    var tagHeader = TagHeader.newBuilder()
                            .setObjectType(ObjectType.valueOf(objectType))
                            .setObjectId(MetadataCodec.encode(new UUID(objectIdHi, objectIdLo)))
                            .setObjectVersion(objectVersion)
                            .setTagVersion(tagVersion)
                            .build();

                    pks[i] = tagPk;
                    versions[i] = tagVersion;
                    tagHeaders[i] = tagHeader;
                }

                if (rs.next())
                    throw new JdbcException(JdbcErrorCode.TOO_MANY_ROWS);

                return new JdbcBaseDal.KeyedItems<>(pks, versions, tagHeaders);
            }
        }
    }

    private Map<String, Value>[]
    fetchTagAttrs(Connection conn, short tenantId, int nTags, int mappingStage) throws SQLException {

        // PKs are inserted into the key mapping table in the order of tag PK
        // We read back attribute records according to those PKs
        // There will be multiple entries per tagPk, i.e. [0, n)
        // The order of attributes within each tag is not known

        var query =
                "select ta.*, km.ordering as tag_index\n" +
                "from key_mapping km\n" +
                "left join tag_attr ta\n" +
                "  on ta.tag_fk = km.pk\n" +
                "where ta.tenant_id = ?\n" +
                "  and km.mapping_stage = ?\n" +
                "order by km.ordering, ta.attr_name, ta.attr_index";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, tenantId);
            stmt.setInt(2, mappingStage);

            try (var rs = stmt.executeQuery()) {

                @SuppressWarnings("unchecked")
                var result = (Map<String, Value>[]) new HashMap[nTags];

                // Start by storing attrs for tag index = 0
                var currentTagAttrs = new HashMap<String, Value>();
                var currentTagIndex = 0;

                var currentAttrArray = new ArrayList<Value>();
                var currentAttrName = "";

                while (rs.next()) {

                    var tagIndex = rs.getInt("tag_index");
                    var attrName = rs.getString("attr_name");
                    var attrIndex = rs.getInt("attr_index");
                    var attrValue = JdbcAttrHelpers.readAttrValue(rs);

                    // Check to see if we have finished processing a multi-valued attr
                    // If so, record it against the last tag and attr name before moving on
                    if (!currentAttrArray.isEmpty()) {
                        if (tagIndex != currentTagIndex || !attrName.equals(currentAttrName)) {

                            var arrayValue = JdbcAttrHelpers.assembleArrayValue(currentAttrArray);
                            currentTagAttrs.put(currentAttrName, arrayValue);

                            currentAttrArray = new ArrayList<>();
                        }
                    }

                    // Check if the current tag index has moved on
                    // If so store accumulated attrs for the previous index
                    while (currentTagIndex != tagIndex) {

                        result[currentTagIndex] = currentTagAttrs;

                        currentTagAttrs = new HashMap<>();
                        currentTagIndex++;
                    }

                    // Sanity check - should never happen
                    if (currentTagIndex >= nTags)
                        throw new JdbcException(JdbcErrorCode.TOO_MANY_ROWS);

                    // Update current attr name
                    currentAttrName = attrName;

                    // Accumulate attr against the current tag index
                    if (attrIndex < 0)
                        currentTagAttrs.put(attrName, attrValue);
                    else
                        currentAttrArray.add(attrValue);
                }

                // Check in case the last attr record was part of a multi-valued attr
                if (!currentAttrArray.isEmpty()) {
                    var arrayValue = JdbcAttrHelpers.assembleArrayValue(currentAttrArray);
                    currentTagAttrs.put(currentAttrName, arrayValue);
                }

                // Store accumulated attrs for the final tag index
                if (nTags > 0)
                    result[currentTagIndex] = currentTagAttrs;

                return result;
            }
        }
    }

    private JdbcBaseDal.KeyedItems<Tag.Builder>
    applyTagAttrs(JdbcBaseDal.KeyedItems<TagHeader> headers, Map<String, Value>[] attrs) {

        var tags = new Tag.Builder[headers.items.length];

        for (var i = 0; i < headers.items.length; i++) {

            tags[i] = Tag.newBuilder()
                    .setHeader(headers.items[i])
                    .putAllAttr(attrs[i]);
        }

        return new JdbcBaseDal.KeyedItems<>(headers.keys, tags);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // KEY LOOKUP FUNCTIONS
    // -----------------------------------------------------------------------------------------------------------------

    long[] lookupObjectPks(Connection conn, short tenantId, UUID[] objectIds) throws SQLException {

        var mappingStage = insertIdForMapping(conn, objectIds);
        mapObjectById(conn, tenantId, mappingStage);

        return fetchMappedPk(conn, mappingStage, objectIds.length);
    }

    long[] lookupDefinitionPk(Connection conn, short tenantId, long[] objectPk, int[] version) throws SQLException {

        var mappingStage = insertFkAndVersionForMapping(conn, objectPk, version);
        mapDefinitionByVersion(conn, tenantId, mappingStage);

        return fetchMappedPk(conn, mappingStage, objectPk.length);
    }

    long[] lookupTagPk(Connection conn, short tenantId, long[] definitionPk, int[] tagVersion) throws SQLException {

        var mappingStage = insertFkAndVersionForMapping(conn, definitionPk, tagVersion);
        mapTagByVersion(conn, tenantId, mappingStage);

        return fetchMappedPk(conn, mappingStage, definitionPk.length);
    }

    private long[] fetchMappedPk(Connection conn, int mappingStage, int length) throws SQLException {

        var query =
                "select pk from key_mapping\n" +
                "where mapping_stage = ?\n" +
                "order by ordering";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, mappingStage);

            try (var rs = stmt.executeQuery()) {

                long[] keys = new long[length];

                for (int i = 0; i < length; i++) {

                    if (!rs.next())
                        throw new JdbcException(JdbcErrorCode.NO_DATA);

                    keys[i] = rs.getLong(1);

                    if (rs.wasNull())
                        throw new JdbcException(JdbcErrorCode.NO_DATA);
                }

                if (rs.next())
                    throw new JdbcException(JdbcErrorCode.TOO_MANY_ROWS);

                return keys;
            }
        }
    }


    // -----------------------------------------------------------------------------------------------------------------
    // KEY MAPPING FUNCTIONS
    // -----------------------------------------------------------------------------------------------------------------

    private int insertIdForMapping(Connection conn, UUID[] ids) throws SQLException {

        var query =
                "insert into key_mapping (id_hi, id_lo, mapping_stage, ordering)\n" +
                "values (?, ?, ?, ?)";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            var mappingStage = nextMappingStage();

            for (var i = 0; i < ids.length; i++) {

                stmt.clearParameters();

                stmt.setLong(1, ids[i].getMostSignificantBits());
                stmt.setLong(2, ids[i].getLeastSignificantBits());
                stmt.setInt(3, mappingStage);
                stmt.setInt(4, i);

                stmt.addBatch();
            }

            stmt.executeBatch();

            return mappingStage;
        }
    }

    private int insertPk(Connection conn, long[] pks) throws SQLException {

        var query =
                "insert into key_mapping (pk, mapping_stage, ordering)\n" +
                "values (?, ?, ?)";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {
            return insertKeysForMapping(stmt, pks);
        }
    }

    private int insertFkForMapping(Connection conn, long[] fks) throws SQLException {

        var query =
                "insert into key_mapping (fk, mapping_stage, ordering)\n" +
                "values (?, ?, ?)";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {
            return insertKeysForMapping(stmt, fks);
        }
    }

    private int insertKeysForMapping(PreparedStatement stmt, long[] keys) throws SQLException {

        var mappingStage = nextMappingStage();

        for (var i = 0; i < keys.length; i++) {

            stmt.clearParameters();

            stmt.setLong(1, keys[i]);
            stmt.setInt(2, mappingStage);
            stmt.setInt(3, i);

            stmt.addBatch();
        }

        stmt.executeBatch();

        return mappingStage;
    }

    private int insertFkAndVersionForMapping(Connection conn, long[] fk, int[] version) throws SQLException {

        var query =
                "insert into key_mapping (fk, ver, mapping_stage, ordering)\n" +
                "values (?, ?, ?, ?)";

        query = query.replaceFirst("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query)) {

            var mappingStage = nextMappingStage();

            for (var i = 0; i < fk.length; i++) {

                stmt.clearParameters();

                stmt.setLong(1, fk[i]);
                stmt.setInt(2, version[i]);
                stmt.setInt(3, mappingStage);
                stmt.setInt(4, i);

                stmt.addBatch();
            }

            stmt.executeBatch();

            return mappingStage;
        }
    }

    private void mapObjectById(Connection conn, short tenantId, int mappingStage) throws SQLException {

        var query =
                "update key_mapping\n" +
                "set pk = (" +
                "  select object_pk from object_id oid\n" +
                "  where oid.tenant_id = ?\n" +
                "  and oid.object_id_hi = key_mapping.id_hi\n" +
                "  and oid.object_id_lo = key_mapping.id_lo)\n" +
                "where mapping_stage = ?";

        query = query.replaceAll("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query))  {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            stmt.execute();
        }
    }

    private void mapDefinitionByVersion(Connection conn, short tenantId, int mappingStage) throws SQLException {

        var query =
                "update key_mapping\n" +
                "set pk = (" +
                "  select definition_pk from object_definition def\n" +
                "  where def.tenant_id = ?\n" +
                "  and def.object_fk = key_mapping.fk\n" +
                "  and def.object_version = key_mapping.ver)\n" +
                "where mapping_stage = ?";

        query = query.replaceAll("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query))  {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            stmt.execute();
        }
    }

    private void mapDefinitionByLatest(Connection conn, short tenantId, int mappingStage) throws SQLException {

        var query =
                "update key_mapping\n" +
                "set pk = (\n" +
                "  select lv.latest_definition_pk\n" +
                "  from latest_version lv\n" +
                "  where lv.tenant_id = ?\n" +
                "  and lv.object_fk = key_mapping.fk)\n" +
                "where mapping_stage = ?";

        query = query.replaceAll("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query))  {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            stmt.execute();
        }
    }

    private void mapTagByVersion(Connection conn, short tenantId, int mappingStage) throws SQLException {

        var query =
                "update key_mapping\n" +
                "set pk = (\n" +
                "  select tag_pk from tag\n" +
                "  where tag.tenant_id = ?\n" +
                "  and tag.definition_fk = key_mapping.fk\n" +
                "  and tag.tag_version = key_mapping.ver)\n" +
                "where mapping_stage = ?";

        query = query.replaceAll("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query))  {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            stmt.execute();
        }
    }

    private void mapTagByLatest(Connection conn, short tenantId, int mappingStage) throws SQLException {

        var query =
                "update key_mapping\n" +
                "set pk = (\n" +
                "  select lt.latest_tag_pk\n" +
                "  from latest_tag lt\n" +
                "  where lt.tenant_id = ?\n" +
                "  and lt.definition_fk = key_mapping.fk)\n" +
                "where mapping_stage = ?";

        query = query.replaceAll("key_mapping", dialect.mappingTableName());

        try (var stmt = conn.prepareStatement(query))  {

            stmt.setShort(1, tenantId);
            stmt.setInt(2, mappingStage);

            stmt.execute();
        }
    }

    private int nextMappingStage() {

        return mappingStage.incrementAndGet();
    }
}
