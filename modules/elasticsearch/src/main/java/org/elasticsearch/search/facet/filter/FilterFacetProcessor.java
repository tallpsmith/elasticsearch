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

package org.elasticsearch.search.facet.filter;

import org.apache.lucene.search.Filter;
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
public class FilterFacetProcessor extends AbstractComponent implements FacetProcessor {

    @Inject public FilterFacetProcessor(Settings settings) {
        super(settings);
        InternalFilterFacet.registerStreams();
    }

    @Override public String[] types() {
        return new String[]{FilterFacet.TYPE};
    }

    @Override public FacetCollector parse(String facetName, XContentParser parser, SearchContext context) throws IOException {
        XContentIndexQueryParser indexQueryParser = (XContentIndexQueryParser) context.queryParser();
        Filter facetFilter = indexQueryParser.parseInnerFilter(parser);
        return new FilterFacetCollector(facetName, facetFilter, context.filterCache());
    }

    @Override public Facet reduce(String name, List<Facet> facets) {
        if (facets.size() == 1) {
            return facets.get(0);
        }
        int count = 0;
        for (Facet facet : facets) {
            count += ((FilterFacet) facet).count();
        }
        return new InternalFilterFacet(name, count);
    }
}
