/*******************************************************************************
 * * Copyright 2015 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.client.hbase.admin;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.util.Bytes;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.impetus.client.hbase.HBaseData;
import com.impetus.client.hbase.Reader;
import com.impetus.client.hbase.Writer;
import com.impetus.client.hbase.service.HBaseReader;
import com.impetus.client.hbase.service.HBaseWriter;
import com.impetus.client.hbase.utils.HBaseUtils;
import com.impetus.kundera.KunderaException;
import com.impetus.kundera.client.EnhanceEntity;
import com.impetus.kundera.db.RelationHolder;
import com.impetus.kundera.metadata.KunderaMetadataManager;
import com.impetus.kundera.metadata.model.EntityMetadata;
import com.impetus.kundera.metadata.model.MetamodelImpl;
import com.impetus.kundera.metadata.model.annotation.DefaultEntityAnnotationProcessor;
import com.impetus.kundera.metadata.model.attributes.AbstractAttribute;
import com.impetus.kundera.metadata.model.type.AbstractManagedType;
import com.impetus.kundera.persistence.EntityManagerFactoryImpl.KunderaMetadata;
import com.impetus.kundera.property.PropertyAccessException;
import com.impetus.kundera.property.PropertyAccessorHelper;
import com.impetus.kundera.utils.KunderaCoreUtils;

/**
 * @author Pragalbh Garg
 * 
 */
public class HBaseDataHandler implements DataHandler
{
    /** the log used by this class. */
    private static Logger log = LoggerFactory.getLogger(HBaseDataHandler.class);

    /** The admin. */
    private HBaseAdmin admin;

    private Connection connection;

    /** The hbase reader. */
    private Reader hbaseReader = new HBaseReader();

    /** The hbase writer. */
    private Writer hbaseWriter = new HBaseWriter();

    private FilterList filter = null;

    private Map<String, FilterList> filters = new ConcurrentHashMap<String, FilterList>();

    private KunderaMetadata kunderaMetadata;

    /**
     * Instantiates a new h base data handler.
     * 
     * @param kunderaMetadata
     *            the kundera metadata
     * @param connection
     *            the connection
     */
    public HBaseDataHandler(final KunderaMetadata kunderaMetadata, final Connection connection)
    {
        try
        {
            this.kunderaMetadata = kunderaMetadata;
            this.connection = connection;
        }
        catch (Exception e)
        {
            throw new PersistenceException(e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#createTableIfDoesNotExist(
     * java.lang.String, java.lang.String[])
     */
    @Override
    public void createTableIfDoesNotExist(final String tableName, final String... colFamily)
            throws MasterNotRunningException, IOException
    {
        if (!admin.tableExists(Bytes.toBytes(tableName)))
        {
            HTableDescriptor htDescriptor = new HTableDescriptor(TableName.valueOf(tableName));
            for (String columnFamily : colFamily)
            {
                HColumnDescriptor familyMetadata = new HColumnDescriptor(columnFamily);
                htDescriptor.addFamily(familyMetadata);
            }
            admin.createTable(htDescriptor);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#readData(java.lang.String,
     * java.lang.Class, com.impetus.kundera.metadata.model.EntityMetadata,
     * java.lang.String, java.util.List)
     */
    @Override
    public List readData(final String tableName, Class clazz, EntityMetadata m, final Object rowKey,
            List<String> relationNames, FilterList f, List<Map<String, Object>> colToOutput) throws IOException
    {

        Object entity = null;

        Table hTable = null;

        hTable = gethTable(tableName);

        if (getFilter(m.getTableName()) != null)
        {
            if (f == null)
            {
                f = new FilterList();
            }
            f.addFilter(getFilter(m.getTableName()));
        }
        List<String> columnsList = new ArrayList<String>();
        if (colToOutput != null && !colToOutput.isEmpty())
        {
            for (Map<String, Object> map : colToOutput)
            {
                columnsList.add(((String) map.get("colName")));
            }
        }
        List<HBaseData> results = new ArrayList<HBaseData>();
        results.addAll(hbaseReader.LoadData(hTable, null, rowKey, f,
                columnsList.toArray(new String[columnsList.size()])));
        return onRead(tableName, clazz, m, colToOutput, hTable, entity, relationNames, results);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#readData(java.lang.String,
     * java.lang.Class, com.impetus.kundera.metadata.model.EntityMetadata,
     * java.lang.String, java.util.List)
     */
    @Override
    public List readAll(final String tableName, Class clazz, EntityMetadata m, final List<Object> rowKey,
            List<String> relationNames, String... columns) throws IOException
    {

        List output = null;

        Object entity = null;

        Table hTable = null;

        hTable = gethTable(tableName);

        MetamodelImpl metaModel = (MetamodelImpl) kunderaMetadata.getApplicationMetadata().getMetamodel(
                m.getPersistenceUnit());

        AbstractManagedType managedType = (AbstractManagedType) metaModel.entity(m.getEntityClazz());
        List<String> secondaryTables = ((DefaultEntityAnnotationProcessor) managedType.getEntityAnnotation())
                .getSecondaryTablesName();
        secondaryTables.add(m.getTableName());
        List<HBaseData> results = new ArrayList<HBaseData>();
        for (String colTableName : secondaryTables)
        {
            List table = ((HBaseReader) hbaseReader).loadAll(hTable, rowKey, colTableName, columns);
            if (table != null)
            {
                results.addAll(table);
            }
        }

        output = onRead(tableName, clazz, m, output, hTable, entity, relationNames, results);
        return output;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#readDataByRange(java.lang.
     * String, java.lang.Class,
     * com.impetus.kundera.metadata.model.EntityMetadata, java.util.List,
     * byte[], byte[])
     */
    @Override
    public List readDataByRange(String tableName, Class clazz, EntityMetadata m, byte[] startRow, byte[] endRow,
            List<Map<String, Object>> colToOutput, FilterList f) throws IOException
    {
        Table hTable = null;
        Object entity = null;
        List<String> relationNames = m.getRelationNames();
        Filter filter = getFilter(m.getTableName());
        if (filter != null)
        {
            if (f == null)
            {
                f = new FilterList();
            }
            f.addFilter(filter);
        }
        List<String> columnsList = new ArrayList<String>();
        if (colToOutput != null && !colToOutput.isEmpty())
        {
            for (Map<String, Object> map : colToOutput)
            {
                columnsList.add(((String) map.get("colName")));
            }
        }
        hTable = gethTable(tableName);
        List<HBaseData> results = hbaseReader.loadAll(hTable, f, startRow, endRow, m.getTableName(), null,
                columnsList.toArray(new String[columnsList.size()]));
        return onRead(tableName, clazz, m, colToOutput, hTable, entity, relationNames, results);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#writeData(java.lang.String,
     * com.impetus.kundera.metadata.model.EntityMetadata, java.lang.Object,
     * java.lang.Object, java.util.List, boolean)
     */
    @Override
    public void writeData(String tableName, EntityMetadata m, Object entity, Object rowId,
            List<RelationHolder> relations, boolean showQuery) throws IOException
    {
        HBaseRow hbaseRow = createHbaseRow(m, entity, rowId, relations);
        writeHbaseRowInATable(tableName, hbaseRow);
    }

    /**
     * Creates the hbase row.
     * 
     * @param m
     *            the m
     * @param entity
     *            the entity
     * @param rowId
     *            the row id
     * @param relations
     *            the relations
     * @return the h base row
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public HBaseRow createHbaseRow(EntityMetadata m, Object entity, Object rowId, List<RelationHolder> relations)
            throws IOException
    {
        MetamodelImpl metaModel = (MetamodelImpl) kunderaMetadata.getApplicationMetadata().getMetamodel(
                m.getPersistenceUnit());
        EntityType entityType = metaModel.entity(m.getEntityClazz());
        Set<Attribute> attributes = entityType.getAttributes();
        if (metaModel.isEmbeddable(m.getIdAttribute().getBindableJavaType()))
        {
            rowId = KunderaCoreUtils.prepareCompositeKey(m, rowId);
        }
        HBaseRow hbaseRow = new HBaseRow(rowId, new ArrayList<HBaseCell>());

        // handle attributes and embeddables
        createCellsAndAddToRow(entity, metaModel, attributes, hbaseRow, m, -1, null);

        // handle relations
        if (relations != null && !relations.isEmpty())
        {
            hbaseRow.addCells(getRelationCell(m, rowId, relations));
        }

        // handle inheritence
        String discrColumn = ((AbstractManagedType) entityType).getDiscriminatorColumn();
        String discrValue = ((AbstractManagedType) entityType).getDiscriminatorValue();
        if (discrColumn != null && discrValue != null)
        {
            hbaseRow.addCell(new HBaseCell(m.getTableName(), discrColumn, discrValue));
        }

        return hbaseRow;
    }

    /**
     * Gets the relation cell.
     * 
     * @param m
     *            the m
     * @param rowId
     *            the row id
     * @param relations
     *            the relations
     * @return the relation cell
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private List<HBaseCell> getRelationCell(EntityMetadata m, Object rowId, List<RelationHolder> relations)
            throws IOException
    {
        List<HBaseCell> relationCells = new ArrayList<HBaseCell>();
        for (RelationHolder relation : relations)
        {
            HBaseCell hBaseCell = new HBaseCell(m.getTableName(), relation.getRelationName(),
                    relation.getRelationValue());
            relationCells.add(hBaseCell);
        }
        return relationCells;
    }

    /**
     * Write hbase row in a table.
     * 
     * @param tableName
     *            the table name
     * @param hbaseRow
     *            the hbase row
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private void writeHbaseRowInATable(String tableName, HBaseRow hbaseRow) throws IOException
    {
        Table hTable = gethTable(tableName);
        ((HBaseWriter) hbaseWriter).writeRow(hTable, hbaseRow);
        hTable.close();
    }

    /**
     * Creates the cells and add to row.
     * 
     * @param entity
     *            the entity
     * @param metaModel
     *            the meta model
     * @param attributes
     *            the attributes
     * @param hbaseRow
     *            the hbase row
     * @param m
     *            the m
     * @param count
     *            the count
     * @param prefix
     *            the prefix
     */
    private void createCellsAndAddToRow(Object entity, MetamodelImpl metaModel, Set<Attribute> attributes,
            HBaseRow hbaseRow, EntityMetadata m, int count, String prefix)
    {
        AbstractAttribute idCol = (AbstractAttribute) m.getIdAttribute();
        for (Attribute attribute : attributes)
        {
            Class clazz = ((AbstractAttribute) attribute).getBindableJavaType();
            if (metaModel.isEmbeddable(clazz))
            {
                Set<Attribute> attribEmbeddables = metaModel.embeddable(
                        ((AbstractAttribute) attribute).getBindableJavaType()).getAttributes();
                Object embeddedField = PropertyAccessorHelper.getObject(entity, (Field) attribute.getJavaMember());
                if (attribute.isCollection())
                {
                    int newCount = count + 1;
                    String newPrefix = prefix != null ? prefix + "#" + attribute.getName() : attribute.getName();
                    for (Object obj : (List) embeddedField)
                    {
                        createCellsAndAddToRow(obj, metaModel, attribEmbeddables, hbaseRow, m, newCount++, newPrefix);
                    }
                }
                else
                {
                    createCellsAndAddToRow(embeddedField, metaModel, attribEmbeddables, hbaseRow, m, -1, prefix);
                }
            }
            else if (!attribute.isCollection() && !attribute.isAssociation())
            {
                String columnFamily = ((AbstractAttribute) attribute).getTableName() != null ? ((AbstractAttribute) attribute)
                        .getTableName() : m.getTableName();
                String columnName = ((AbstractAttribute) attribute).getJPAColumnName();
                columnName = count != -1 ? prefix + "#" + columnName + "#" + count : columnName;
                Object value = PropertyAccessorHelper.getObject(entity, (Field) attribute.getJavaMember());
                HBaseCell hbaseCell = new HBaseCell(columnFamily, columnName, value);
                if (!idCol.getName().equals(attribute.getName()) && value != null)
                {
                    hbaseRow.addCell(hbaseCell);
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#writeData(java.lang.String,
     * com.impetus.kundera.metadata.model.EntityMetadata, java.lang.Object,
     * java.lang.String, java.util.List)
     */

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#writeJoinTableData(java.lang
     * .String, java.lang.String, java.util.Map)
     */
    @Override
    public void writeJoinTableData(String tableName, Object rowId, Map<String, Object> columns, String columnFamilyName)
            throws IOException
    {
        Table hTable = gethTable(tableName);

        hbaseWriter.writeColumns(hTable, rowId, columns, columnFamilyName);

        closeHTable(hTable);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#getForeignKeysFromJoinTable
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public <E> List<E> getForeignKeysFromJoinTable(String schemaName, String joinTableName, Object rowKey,
            String inverseJoinColumnName)
    {
        List<E> foreignKeys = new ArrayList<E>();

        Table hTable = null;

        // Load raw data from Join Table in HBase
        try
        {
            hTable = gethTable(schemaName);

            List<HBaseData> results = hbaseReader.LoadData(hTable, joinTableName, rowKey, getFilter(joinTableName));

            // assuming rowKey is not null.
            if (results != null)
            {

                HBaseData data = results.get(0);

                Map<String, byte[]> hbaseValues = data.getColumns();
                Set<String> columnNames = hbaseValues.keySet();

                for (String columnName : columnNames)
                {
                    if (columnName.startsWith(inverseJoinColumnName) && data.getColumnFamily().equals(joinTableName))
                    {
                        byte[] columnValue = data.getColumnValue(columnName);

                        // TODO : Because no attribute class is present, so
                        // cannot be done.
                        String hbaseColumnValue = Bytes.toString(columnValue);

                        foreignKeys.add((E) hbaseColumnValue);
                    }
                }
            }
        }
        catch (IOException e)
        {
            return foreignKeys;
        }
        finally
        {
            try
            {
                if (hTable != null)
                {
                    closeHTable(hTable);
                }
            }
            catch (IOException e)
            {

                // Do nothing.
            }
        }
        return foreignKeys;
    }

    /**
     * Gets the h table.
     * 
     * @param tableName
     *            the table name
     * @return the h table
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public Table gethTable(final String tableName) throws IOException
    {
        return connection.getTable(TableName.valueOf(tableName));
    }

    /**
     * Puth table.
     * 
     * @param hTable
     *            the h table
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private void closeHTable(Table hTable) throws IOException
    {
        hTable.close();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.impetus.client.hbase.admin.DataHandler#shutdown()
     */
    @Override
    public void shutdown()
    {

        // TODO: Shutting down admin actually shuts down HMaster, something we
        // don't want.
        // Devise a better way to release resources.

        /*
         * try {
         * 
         * admin.shutdown();
         * 
         * } catch (IOException e) { throw new RuntimeException(e.getMessage());
         * }
         */
    }

    /**
     * Populate entity from h base data.
     * 
     * @param entity
     *            the entity
     * @param hbaseData
     *            the hbase data
     * @param m
     *            the m
     * @param rowKey
     *            the row key
     * @param relationNames
     *            the relation names
     * @return the object
     */
    private Object populateEntityFromHBaseData(Object entity, HBaseData hbaseData, EntityMetadata m, Object rowKey,
            List<String> relationNames)
    {
        try
        {
            Map<String, Object> relations = new HashMap<String, Object>();

            if (entity.getClass().isAssignableFrom(EnhanceEntity.class))
            {
                relations = ((EnhanceEntity) entity).getRelations();
                entity = ((EnhanceEntity) entity).getEntity();

            }

            MetamodelImpl metaModel = (MetamodelImpl) kunderaMetadata.getApplicationMetadata().getMetamodel(
                    m.getPersistenceUnit());
            EntityType entityType = metaModel.entity(m.getEntityClazz());

            Set<Attribute> attributes = entityType.getAttributes();

            String discrColumn = ((AbstractManagedType) entityType).getDiscriminatorColumn();
            String discrValue = ((AbstractManagedType) entityType).getDiscriminatorValue();

            if (discrColumn != null && hbaseData.getColumnValue(discrColumn) != null && discrValue != null)
            {
                byte[] discrimnatorValue = hbaseData.getColumnValue(discrColumn);
                String actualDiscriminatorValue = Bytes.toString(discrimnatorValue);
                if (actualDiscriminatorValue != null && !actualDiscriminatorValue.equals(discrValue))
                {
                    return null;
                }
            }

            writeValuesToEntity(entity, hbaseData, m, metaModel, attributes, relationNames, relations, -1, null);
            if (!relations.isEmpty())
            {

                return new EnhanceEntity(entity, rowKey, relations);
            }
            return entity;
        }
        catch (PropertyAccessException e1)
        {
            throw new RuntimeException(e1);
        }
    }

    /**
     * Write values to entity.
     * 
     * @param entity
     *            the entity
     * @param hbaseData
     *            the hbase data
     * @param m
     *            the m
     * @param metaModel
     *            the meta model
     * @param attributes
     *            the attributes
     * @param relationNames
     *            the relation names
     * @param relations
     *            the relations
     * @param count
     *            the count
     * @param prefix
     *            the prefix
     * @return the int
     */
    private int writeValuesToEntity(Object entity, HBaseData hbaseData, EntityMetadata m, MetamodelImpl metaModel,
            Set<Attribute> attributes, List<String> relationNames, Map<String, Object> relations, int count,
            String prefix)
    {
        int check = 0;
        for (Attribute attribute : attributes)
        {
            Class javaType = ((AbstractAttribute) attribute).getBindableJavaType();
            if (metaModel.isEmbeddable(javaType))
            {
                Set<Attribute> attribEmbeddables = metaModel.embeddable(javaType).getAttributes();
                Object embeddedField = KunderaCoreUtils.createNewInstance(javaType);
                if (!attribute.isCollection())
                {
                    writeValuesToEntity(embeddedField, hbaseData, m, metaModel, attribEmbeddables, null, null, -1,
                            prefix);
                    PropertyAccessorHelper.set(entity, (Field) attribute.getJavaMember(), embeddedField);
                }
                else
                {
                    int newCount = count + 1;
                    String newPrefix = prefix != null ? prefix + "#" + attribute.getName() : attribute.getName();
                    List embeddedCollection = new ArrayList();
                    Boolean f = true;
                    while (f)
                    {
                        embeddedField = KunderaCoreUtils.createNewInstance(javaType);
                        int checkEnd = writeValuesToEntity(embeddedField, hbaseData, m, metaModel, attribEmbeddables,
                                null, null, newCount++, newPrefix);
                        if (checkEnd == 0)
                        {
                            f = false;
                        }
                        else
                        {
                            embeddedCollection.add(embeddedField);
                        }
                    }
                    PropertyAccessorHelper.set(entity, (Field) attribute.getJavaMember(), embeddedCollection);
                }
            }
            else if (!attribute.isCollection())
            {
                String columnName = ((AbstractAttribute) attribute).getJPAColumnName();
                columnName = count != -1 ? prefix + "#" + columnName + "#" + count : columnName;
                String idColName = ((AbstractAttribute) m.getIdAttribute()).getJPAColumnName();
                String colFamily = ((AbstractAttribute) attribute).getTableName() != null ? ((AbstractAttribute) attribute)
                        .getTableName() : m.getTableName();
                byte[] columnValue = hbaseData.getColumnValue(HBaseUtils.getColumnDataKey(colFamily, columnName));
                if (relationNames != null && relationNames.contains(columnName) && columnValue != null
                        && columnValue.length > 0)
                {
                    EntityType entityType = metaModel.entity(m.getEntityClazz());
                    relations.put(columnName, getObjectFromByteArray(entityType, columnValue, columnName, m));
                }
                else if (!idColName.equals(columnName) && columnValue != null)
                {
                    PropertyAccessorHelper.set(entity, (Field) attribute.getJavaMember(), columnValue);
                    check++;
                }
            }
        }
        return check;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#deleteRow(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void deleteRow(Object rowKey, String tableName) throws IOException
    {
        Table hTable = gethTable(tableName);
        hbaseWriter.delete(hTable, rowKey);
        closeHTable(hTable);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#findParentEntityFromJoinTable
     * (com.impetus.kundera.metadata.model.EntityMetadata, java.lang.String,
     * java.lang.String, java.lang.String, java.lang.Object)
     */
    @Override
    public List<Object> findParentEntityFromJoinTable(EntityMetadata parentMetadata, String joinTableName,
            String joinColumnName, String inverseJoinColumnName, Object childId)
    {
        throw new PersistenceException("Not applicable for HBase");
    }

    /**
     * Sets the filter.
     * 
     * @param filter
     *            the new filter
     */
    public void setFilter(Filter filter)
    {
        if (this.filter == null)
        {
            this.filter = new FilterList();
        }
        if (filter != null)
        {
            this.filter.addFilter(filter);
        }
    }

    /**
     * Adds the filter.
     * 
     * @param columnFamily
     *            the column family
     * @param filter
     *            the filter
     */
    public void addFilter(final String columnFamily, Filter filter)
    {
        FilterList filterList = this.filters.get(columnFamily);
        if (filterList == null)
        {
            filterList = new FilterList();
        }
        if (filter != null)
        {
            filterList.addFilter(filter);
        }
        this.filters.put(columnFamily, filterList);
    }

    /**
     * On read.
     * 
     * @param tableName
     *            the table name
     * @param clazz
     *            the clazz
     * @param m
     *            the m
     * @param columnsToOutput
     *            the columns to output
     * @param hTable
     *            the h table
     * @param entity
     *            the entity
     * @param relationNames
     *            the relation names
     * @param results
     *            the results
     * @return the list
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private List onRead(String tableName, Class clazz, EntityMetadata m, List<Map<String, Object>> columnsToOutput,
            Table hTable, Object entity, List<String> relationNames, List<HBaseData> results) throws IOException
    {
        List outputResults = new ArrayList();
        try
        {
            if (columnsToOutput != null && !columnsToOutput.isEmpty())
            {
                for (HBaseData data : results)
                {
                    List result = new ArrayList();
                    Map<String, byte[]> columns = data.getColumns();
                    for (Map<String, Object> map : columnsToOutput)
                    {
                        Object obj;
                        String colDataKey = HBaseUtils.getColumnDataKey((String) map.get("colFamily"),
                                (String) map.get("colName"));
                        if (!(boolean) map.get("isEmbeddable"))
                        {
                            obj = HBaseUtils.fromBytes(columns.get(colDataKey), (Class) map.get("fieldClazz"));
                        }
                        else
                        {
                            Class embedClazz = (Class) map.get("fieldClazz");
                            obj = populateEmbeddableObject(data, KunderaCoreUtils.createNewInstance(embedClazz), m,
                                    embedClazz);
                        }
                        result.add(obj);
                    }
                    if (columnsToOutput.size() == 1)
                        outputResults.addAll(result);
                    else
                        outputResults.add(result);
                }
            }
            else
            {
                Map<Object, Object> entityListMap = new HashMap<Object, Object>();

                for (HBaseData data : results)
                {
                    entity = KunderaCoreUtils.createNewInstance(clazz); // Entity
                                                                        // Object
                    MetamodelImpl metaModel = (MetamodelImpl) kunderaMetadata.getApplicationMetadata().getMetamodel(
                            m.getPersistenceUnit());
                    Object rowKeyValue = HBaseUtils.fromBytes(m, metaModel, data.getRowKey());
                    if (!metaModel.isEmbeddable(m.getIdAttribute().getBindableJavaType()))
                    {
                        PropertyAccessorHelper.setId(entity, m, rowKeyValue);
                    }

                    if (entityListMap.get(rowKeyValue) != null)
                    {

                        entity = entityListMap.get(rowKeyValue);

                    }
                    entity = populateEntityFromHBaseData(entity, data, m, null, relationNames);
                    if (entity != null)
                    {
                        entityListMap.put(rowKeyValue, entity);

                    }
                }
                for (Object obj : entityListMap.values())
                {
                    outputResults.add(obj);
                }
            }

            if (results != null)
            {

            }
        }
        catch (Exception e)
        {
            log.error("Error while creating an instance of {}, Caused by: .", clazz, e);
            throw new PersistenceException(e);
        }
        finally
        {
            if (hTable != null)
            {
                closeHTable(hTable);
            }
        }

        return outputResults;
    }

    /**
     * Populate embeddable object.
     * 
     * @param data
     *            the data
     * @param obj
     *            the obj
     * @param m
     *            the m
     * @param clazz
     *            the clazz
     * @return the object
     */
    private Object populateEmbeddableObject(HBaseData data, Object obj, EntityMetadata m, Class clazz)
    {
        MetamodelImpl metaModel = (MetamodelImpl) kunderaMetadata.getApplicationMetadata().getMetamodel(
                m.getPersistenceUnit());
        Set<Attribute> attributes = metaModel.embeddable(clazz).getAttributes();
        writeValuesToEntity(obj, data, m, metaModel, attributes, null, null, -1, null);
        return obj;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#preparePut(com.impetus.client
     * .hbase.admin.HBaseRow)
     */
    @Override
    public Put preparePut(HBaseRow hbaseRow)
    {
        return ((HBaseWriter) hbaseWriter).preparePut(hbaseRow);
    }

    /**
     * Scan data.
     * 
     * @param f
     *            the f
     * @param tableName
     *            the table name
     * @param clazz
     *            the clazz
     * @param m
     *            the m
     * @param columnFamily
     *            the column family
     * @param qualifier
     *            the qualifier
     * @return the list
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws InstantiationException
     *             the instantiation exception
     * @throws IllegalAccessException
     *             the illegal access exception
     */
    public List scanData(Filter f, final String tableName, Class clazz, EntityMetadata m, String columnFamily,
            String qualifier) throws IOException, InstantiationException, IllegalAccessException
    {
        List returnedResults = new ArrayList();
        MetamodelImpl metaModel = (MetamodelImpl) kunderaMetadata.getApplicationMetadata().getMetamodel(
                m.getPersistenceUnit());
        EntityType entityType = metaModel.entity(m.getEntityClazz());
        Set<Attribute> attributes = entityType.getAttributes();
        String[] columns = new String[attributes.size() - 1];
        int count = 0;
        boolean isCollection = false;
        for (Attribute attr : attributes)
        {
            if (!attr.isCollection() && !attr.getName().equalsIgnoreCase(m.getIdAttribute().getName()))
            {
                columns[count++] = ((AbstractAttribute) attr).getJPAColumnName();
            }
            else if (attr.isCollection())
            {
                isCollection = true;
                break;
            }
        }
        List<HBaseData> results = hbaseReader.loadAll(gethTable(tableName), f, null, null, m.getTableName(),
                isCollection ? qualifier : null, null);
        if (results != null)
        {
            for (HBaseData row : results)
            {
                Object entity = clazz.newInstance();// Entity Object
                /* Set Row Key */
                PropertyAccessorHelper.setId(entity, m, HBaseUtils.fromBytes(m, metaModel, row.getRowKey()));

                returnedResults.add(populateEntityFromHBaseData(entity, row, m, row.getRowKey(), m.getRelationNames()));
            }
        }
        return returnedResults;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#scanRowyKeys(org.apache.hadoop
     * .hbase.filter.FilterList, java.lang.String, java.lang.String,
     * java.lang.String, java.lang.Class)
     */
    @Override
    public Object[] scanRowyKeys(FilterList filterList, String tableName, String columnFamilyName, String columnName,
            final Class rowKeyClazz) throws IOException
    {
        Table hTable = null;
        hTable = gethTable(tableName);
        return hbaseReader.scanRowKeys(hTable, filterList, columnFamilyName, columnName, rowKeyClazz);
    }

    /**
     * Gets the object from byte array.
     * 
     * @param entityType
     *            the entity type
     * @param value
     *            the value
     * @param jpaColumnName
     *            the jpa column name
     * @param m
     *            the m
     * @return the object from byte array
     */
    private Object getObjectFromByteArray(EntityType entityType, byte[] value, String jpaColumnName, EntityMetadata m)
    {
        if (jpaColumnName != null)
        {
            String fieldName = m.getFieldName(jpaColumnName);
            if (fieldName != null)
            {
                Attribute attribute = fieldName != null ? entityType.getAttribute(fieldName) : null;

                EntityMetadata relationMetadata = KunderaMetadataManager.getEntityMetadata(kunderaMetadata,
                        attribute.getJavaType());
                Object colValue = PropertyAccessorHelper.getObject(relationMetadata.getIdAttribute().getJavaType(),
                        value);
                return colValue;
            }
        }
        log.warn("No value found for column {}, returning null.", jpaColumnName);
        return null;
    }

    // /**
    // * @param data
    // * @throws IOException
    // */
    // public void batch_insert(Map<Table, List<HBaseDataWrapper>> data) throws
    // IOException
    // {
    // hbaseWriter.persistRows(data);
    // }

    /**
     * Sets the fetch size.
     * 
     * @param fetchSize
     *            the new fetch size
     */
    public void setFetchSize(final int fetchSize)
    {
        ((HBaseReader) hbaseReader).setFetchSize(fetchSize);
    }

    /**
     * Next.
     * 
     * @param m
     *            the m
     * @return the object
     */
    public Object next(EntityMetadata m)
    {
        Object entity = null;
        HBaseData result = ((HBaseReader) hbaseReader).next();
        List<HBaseData> results = new ArrayList<HBaseData>();
        List output = new ArrayList();
        results.add(result);
        try
        {
            output = onRead(m.getSchema(), m.getEntityClazz(), m, output, gethTable(m.getSchema()), entity,
                    m.getRelationNames(), results);
        }
        catch (IOException e)
        {
            log.error("Error during finding next record, Caused by: .", e);
            throw new KunderaException(e);
        }

        return output != null && !output.isEmpty() ? output.get(0) : output;
    }

    /**
     * Checks for next.
     * 
     * @return true, if successful
     */
    public boolean hasNext()
    {
        return ((HBaseReader) hbaseReader).hasNext();
    }

    /**
     * Reset.
     */
    public void reset()
    {
        resetFilter();
        ((HBaseReader) hbaseReader).reset();
    }

    /**
     * Reset filter.
     */
    public void resetFilter()
    {
        filter = null;
        filters = new ConcurrentHashMap<String, FilterList>();
    }

    /**
     * Gets the handle.
     * 
     * @return the handle
     */
    public HBaseDataHandler getHandle()
    {
        HBaseDataHandler handler = new HBaseDataHandler(this.kunderaMetadata, this.connection);
        handler.filter = this.filter;
        handler.filters = this.filters;
        return handler;
    }

    /**
     * Gets the filter.
     * 
     * @param columnFamily
     *            the column family
     * @return the filter
     */
    private Filter getFilter(final String columnFamily)
    {
        FilterList filter = filters.get(columnFamily);
        if (filter == null)
        {
            return this.filter;
        }
        if (this.filter != null)
        {
            filter.addFilter(this.filter);
        }
        return filter;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#prepareDelete(java.lang.Object
     * )
     */
    @Override
    public Row prepareDelete(Object rowKey)
    {
        byte[] rowBytes = HBaseUtils.getBytes(rowKey);
        return new Delete(rowBytes);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.client.hbase.admin.DataHandler#batchProcess(java.util.Map)
     */
    @Override
    public void batchProcess(Map<String, List<Row>> batchData)
    {
        for (String tableName : batchData.keySet())
        {
            List<Row> actions = batchData.get(tableName);
            try
            {
                Table hTable = gethTable(tableName);
                hTable.batch(actions, new Object[actions.size()]);
            }
            catch (IOException | InterruptedException e)
            {
                log.error("Error while batch processing on HTable: " + tableName);
                throw new PersistenceException(e);
            }

        }

    }
}