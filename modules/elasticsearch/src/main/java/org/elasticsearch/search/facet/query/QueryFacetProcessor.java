/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.facet.query;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.xcontent.XContentIndexQueryParser;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.FacetCollector;
import org.elasticsearch.search.facet.FacetProcessor;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.List;

/**
 * @author kimchy (shay.banon)
 */
public class QueryFacetProcessor extends AbstractComponent implements FacetProcessor {

    @Inject public QueryFacetProcessor(Settings settings) {
        super(settings);
        InternalQueryFacet.registerStreams();
    }

    @Override public String[] types() {
        return new String[]{QueryFacet.TYPE};
    }

    @Override public FacetCollector parse(String facetName, XContentParser parser, SearchContext context) throws IOException {
        XContentIndexQueryParser indexQueryParser = (XContentIndexQueryParser) context.queryParser();
        Query facetQuery = indexQueryParser.parse(parser).query();
        return new QueryFacetCollector(facetName, facetQuery, context.filterCache());
    }

    @Override public Facet reduce(String name, List<Facet> facets) {
        if (facets.size() == 1) {
            return facets.get(0);
        }
        int count = 0;
        for (Facet facet : facets) {
            if (facet.name().equals(name)) {
                count += ((QueryFacet) facet).count();
            }
        }
        return new InternalQueryFacet(name, count);
    }
}
