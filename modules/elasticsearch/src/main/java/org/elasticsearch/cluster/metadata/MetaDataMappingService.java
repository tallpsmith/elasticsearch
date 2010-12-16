/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateUpdateTask;
import org.elasticsearch.cluster.ProcessedClusterStateUpdateTask;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.compress.CompressedString;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.index.service.IndexService;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.indices.InvalidTypeNameException;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.cluster.ClusterState.*;
import static org.elasticsearch.cluster.metadata.IndexMetaData.*;
import static org.elasticsearch.cluster.metadata.MetaData.*;
import static org.elasticsearch.common.collect.Maps.*;
import static org.elasticsearch.index.mapper.DocumentMapper.MergeFlags.*;

/**
 * @author kimchy (shay.banon)
 */
public class MetaDataMappingService extends AbstractComponent {

    private final ClusterService clusterService;

    private final IndicesService indicesService;

    @Inject public MetaDataMappingService(Settings settings, ClusterService clusterService, IndicesService indicesService) {
        super(settings);
        this.clusterService = clusterService;
        this.indicesService = indicesService;
    }

    public void updateMapping(final String index, final String type, final CompressedString mappingSource) {
        clusterService.submitStateUpdateTask("update-mapping [" + index + "][" + type + "]", new ClusterStateUpdateTask() {
            @Override public ClusterState execute(ClusterState currentState) {
                try {
                    // first, check if it really needs to be updated
                    final IndexMetaData indexMetaData = currentState.metaData().index(index);
                    if (indexMetaData == null) {
                        // index got delete on us, ignore...
                        return currentState;
                    }
                    if (indexMetaData.mappings().containsKey(type) && indexMetaData.mapping(type).source().equals(mappingSource)) {
                        return currentState;
                    }

                    IndexService indexService = indicesService.indexService(index);
                    if (indexService == null) {
                        // we need to create the index here, and add the current mapping to it, so we can merge
                        indexService = indicesService.createIndex(indexMetaData.index(), indexMetaData.settings(), currentState.nodes().localNode().id());
                        // only add the current relevant mapping (if exists)
                        if (indexMetaData.mappings().containsKey(type)) {
                            indexService.mapperService().add(type, indexMetaData.mappings().get(type).source().string());
                        }
                    }
                    MapperService mapperService = indexService.mapperService();

                    DocumentMapper existingMapper = mapperService.documentMapper(type);
                    // parse the updated one
                    DocumentMapper updatedMapper = mapperService.parse(type, mappingSource.string());
                    if (existingMapper == null) {
                        existingMapper = updatedMapper;
                    } else {
                        // merge from the updated into the existing, ignore conflicts (we know we have them, we just want the new ones)
                        existingMapper.merge(updatedMapper, mergeFlags().simulate(false));
                    }

                    // if we end up with the same mapping as the original once, ignore
                    if (indexMetaData.mappings().containsKey(type) && indexMetaData.mapping(type).source().equals(existingMapper.mappingSource())) {
                        return currentState;
                    }

                    // build the updated mapping source
                    if (logger.isDebugEnabled()) {
                        try {
                            logger.debug("[{}] update_mapping [{}] (dynamic) with source [{}]", index, type, existingMapper.mappingSource().string());
                        } catch (IOException e) {
                            // ignore
                        }
                    } else if (logger.isInfoEnabled()) {
                        logger.info("[{}] update_mapping [{}] (dynamic)", index, type);
                    }

                    MetaData.Builder builder = newMetaDataBuilder().metaData(currentState.metaData());
                    builder.put(newIndexMetaDataBuilder(indexMetaData).putMapping(new MappingMetaData(existingMapper)));
                    return newClusterStateBuilder().state(currentState).metaData(builder).build();
                } catch (Exception e) {
                    logger.warn("failed to dynamically update the mapping in cluster_state from shard", e);
                    return currentState;
                }
            }
        });
    }

    public void removeMapping(final RemoveRequest request) {
        clusterService.submitStateUpdateTask("remove-mapping [" + request.mappingType + "]", new ProcessedClusterStateUpdateTask() {
            @Override public ClusterState execute(ClusterState currentState) {
                if (request.indices.length == 0) {
                    throw new IndexMissingException(new Index("_all"));
                }

                logger.info("[{}] remove_mapping [{}]", request.indices, request.mappingType);
                MetaData.Builder builder = newMetaDataBuilder().metaData(currentState.metaData());
                for (String indexName : request.indices) {
                    if (currentState.metaData().hasIndex(indexName)) {
                        builder.put(newIndexMetaDataBuilder(currentState.metaData().index(indexName)).removeMapping(request.mappingType));
                    }
                }

                return ClusterState.builder().state(currentState).metaData(builder).build();
            }

            @Override public void clusterStateProcessed(ClusterState clusterState) {
                // TODO add a listener here!
            }
        });
    }

    public void putMapping(final PutRequest request, final Listener listener) {
        clusterService.submitStateUpdateTask("put-mapping [" + request.mappingType + "]", new ProcessedClusterStateUpdateTask() {
            @Override public ClusterState execute(ClusterState currentState) {
                try {
                    if (request.indices.length == 0) {
                        throw new IndexMissingException(new Index("_all"));
                    }
                    for (String index : request.indices) {
                        if (!currentState.metaData().hasIndex(index)) {
                            listener.onFailure(new IndexMissingException(new Index(index)));
                        }
                    }

                    // pre create indices here and add mappings to them so we can merge the mappings here if needed
                    for (String index : request.indices) {
                        if (indicesService.hasIndex(index)) {
                            continue;
                        }
                        final IndexMetaData indexMetaData = currentState.metaData().index(index);
                        IndexService indexService = indicesService.createIndex(indexMetaData.index(), indexMetaData.settings(), currentState.nodes().localNode().id());
                        // only add the current relevant mapping (if exists)
                        if (indexMetaData.mappings().containsKey(request.mappingType)) {
                            indexService.mapperService().add(request.mappingType, indexMetaData.mappings().get(request.mappingType).source().string());
                        }
                    }

                    Map<String, DocumentMapper> newMappers = newHashMap();
                    Map<String, DocumentMapper> existingMappers = newHashMap();
                    for (String index : request.indices) {
                        IndexService indexService = indicesService.indexService(index);
                        if (indexService != null) {
                            // try and parse it (no need to add it here) so we can bail early in case of parsing exception
                            DocumentMapper newMapper = indexService.mapperService().parse(request.mappingType, request.mappingSource);
                            newMappers.put(index, newMapper);
                            DocumentMapper existingMapper = indexService.mapperService().documentMapper(request.mappingType);
                            if (existingMapper != null) {
                                // first, simulate
                                DocumentMapper.MergeResult mergeResult = existingMapper.merge(newMapper, mergeFlags().simulate(true));
                                // if we have conflicts, and we are not supposed to ignore them, throw an exception
                                if (!request.ignoreConflicts && mergeResult.hasConflicts()) {
                                    throw new MergeMappingException(mergeResult.conflicts());
                                }
                                existingMappers.put(index, existingMapper);
                            }
                        } else {
                            throw new IndexMissingException(new Index(index));
                        }
                    }

                    String mappingType = request.mappingType;
                    if (mappingType == null) {
                        mappingType = newMappers.values().iterator().next().type();
                    } else if (!mappingType.equals(newMappers.values().iterator().next().type())) {
                        throw new InvalidTypeNameException("Type name provided does not match type name within mapping definition");
                    }
                    if (!MapperService.DEFAULT_MAPPING.equals(mappingType) && mappingType.charAt(0) == '_') {
                        throw new InvalidTypeNameException("Document mapping type name can't start with '_'");
                    }

                    final Map<String, MappingMetaData> mappings = newHashMap();
                    for (Map.Entry<String, DocumentMapper> entry : newMappers.entrySet()) {
                        String index = entry.getKey();
                        // do the actual merge here on the master, and update the mapping source
                        DocumentMapper newMapper = entry.getValue();
                        if (existingMappers.containsKey(entry.getKey())) {
                            // we have an existing mapping, do the merge here (on the master), it will automatically update the mapping source
                            DocumentMapper existingMapper = existingMappers.get(entry.getKey());
                            CompressedString existingSource = existingMapper.mappingSource();

                            existingMapper.merge(newMapper, mergeFlags().simulate(false));

                            CompressedString updatedSource = existingMapper.mappingSource();
                            if (existingSource.equals(updatedSource)) {
                                // same source, no changes, ignore it
                            } else {
                                // use the merged mapping source
                                mappings.put(index, new MappingMetaData(existingMapper));
                                if (logger.isDebugEnabled()) {
                                    logger.debug("[{}] update_mapping [{}] with source [{}]", index, existingMapper.type(), updatedSource);
                                } else if (logger.isInfoEnabled()) {
                                    logger.info("[{}] update_mapping [{}]", index, existingMapper.type());
                                }
                            }
                        } else {
                            CompressedString newSource = newMapper.mappingSource();
                            mappings.put(index, new MappingMetaData(newMapper));
                            if (logger.isDebugEnabled()) {
                                logger.debug("[{}] create_mapping [{}] with source [{}]", index, newMapper.type(), newSource);
                            } else if (logger.isInfoEnabled()) {
                                logger.info("[{}] create_mapping [{}]", index, newMapper.type());
                            }
                        }
                    }

                    if (mappings.isEmpty()) {
                        // no changes, return
                        listener.onResponse(new Response(true));
                        return currentState;
                    }

                    MetaData.Builder builder = newMetaDataBuilder().metaData(currentState.metaData());
                    for (String indexName : request.indices) {
                        IndexMetaData indexMetaData = currentState.metaData().index(indexName);
                        if (indexMetaData == null) {
                            throw new IndexMissingException(new Index(indexName));
                        }
                        MappingMetaData mappingMd = mappings.get(indexName);
                        if (mappingMd != null) {
                            builder.put(newIndexMetaDataBuilder(indexMetaData).putMapping(mappingMd));
                        }
                    }

                    return newClusterStateBuilder().state(currentState).metaData(builder).build();
                } catch (Exception e) {
                    listener.onFailure(e);
                    return currentState;
                }
            }

            @Override public void clusterStateProcessed(ClusterState clusterState) {
                listener.onResponse(new Response(true));
            }
        });
    }

    public static interface Listener {

        void onResponse(Response response);

        void onFailure(Throwable t);
    }

    public static class RemoveRequest {

        final String[] indices;

        final String mappingType;

        public RemoveRequest(String[] indices, String mappingType) {
            this.indices = indices;
            this.mappingType = mappingType;
        }
    }

    public static class PutRequest {

        final String[] indices;

        final String mappingType;

        final String mappingSource;

        boolean ignoreConflicts = false;

        TimeValue timeout = TimeValue.timeValueSeconds(10);

        public PutRequest(String[] indices, String mappingType, String mappingSource) {
            this.indices = indices;
            this.mappingType = mappingType;
            this.mappingSource = mappingSource;
        }

        public PutRequest ignoreConflicts(boolean ignoreConflicts) {
            this.ignoreConflicts = ignoreConflicts;
            return this;
        }

        public PutRequest timeout(TimeValue timeout) {
            this.timeout = timeout;
            return this;
        }
    }

    public static class Response {
        private final boolean acknowledged;

        public Response(boolean acknowledged) {
            this.acknowledged = acknowledged;
        }

        public boolean acknowledged() {
            return acknowledged;
        }
    }
}
