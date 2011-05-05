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

package org.elasticsearch.search.facet.datehistogram;

import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilderException;
import org.elasticsearch.search.facet.AbstractFacetBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * A facet builder of date histogram facets.
 *
 * @author kimchy (shay.banon)
 */
public class DateHistogramFacetBuilder extends AbstractFacetBuilder {
    private String keyFieldName;
    private String valueFieldName;
    private String interval = null;
    private String zone = null;
    private DateHistogramFacet.ComparatorType comparatorType;

    private String valueScript;
    private Map<String, Object> params;
    private String lang;

    /**
     * Constructs a new date histogram facet with the provided facet logical name.
     *
     * @param name The logical name of the facet
     */
    public DateHistogramFacetBuilder(String name) {
        super(name);
    }

    /**
     * The field name to perform the histogram facet. Translates to perform the histogram facet
     * using the provided field as both the {@link #keyField(String)} and {@link #valueField(String)}.
     */
    public DateHistogramFacetBuilder field(String field) {
        this.keyFieldName = field;
        return this;
    }

    /**
     * The field name to use in order to control where the hit will "fall into" within the histogram
     * entries. Essentially, using the key field numeric value, the hit will be "rounded" into the relevant
     * bucket controlled by the interval.
     */
    public DateHistogramFacetBuilder keyField(String keyField) {
        this.keyFieldName = keyField;
        return this;
    }

    /**
     * The field name to use as the value of the hit to compute data based on values within the interval
     * (for example, total).
     */
    public DateHistogramFacetBuilder valueField(String valueField) {
        this.valueFieldName = valueField;
        return this;
    }

    public DateHistogramFacetBuilder valueScript(String valueScript) {
        this.valueScript = valueScript;
        return this;
    }

    public DateHistogramFacetBuilder param(String name, Object value) {
        if (params == null) {
            params = Maps.newHashMap();
        }
        params.put(name, value);
        return this;
    }

    /**
     * The language of the value script.
     */
    public DateHistogramFacetBuilder lang(String lang) {
        this.lang = lang;
        return this;
    }

    /**
     * The interval used to control the bucket "size" where each key value of a hit will fall into. Check
     * the docs for all available values.
     */
    public DateHistogramFacetBuilder interval(String interval) {
        this.interval = interval;
        return this;
    }

    /**
     * Sets the time zone to use when bucketing the values. Can either be in the form of "-10:00" or
     * one of the values listed here: http://joda-time.sourceforge.net/timezones.html.
     */
    public DateHistogramFacetBuilder zone(String zone) {
        this.zone = zone;
        return this;
    }

    public DateHistogramFacetBuilder comparator(DateHistogramFacet.ComparatorType comparatorType) {
        this.comparatorType = comparatorType;
        return this;
    }

    /**
     * Should the facet run in global mode (not bounded by the search query) or not (bounded by
     * the search query). Defaults to <tt>false</tt>.
     */
    public DateHistogramFacetBuilder global(boolean global) {
        super.global(global);
        return this;
    }

    /**
     * Marks the facet to run in a specific scope.
     */
    @Override public DateHistogramFacetBuilder scope(String scope) {
        super.scope(scope);
        return this;
    }

    /**
     * An additional filter used to further filter down the set of documents the facet will run on.
     */
    public DateHistogramFacetBuilder facetFilter(XContentFilterBuilder filter) {
        this.facetFilter = filter;
        return this;
    }

    @Override public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (keyFieldName == null) {
            throw new SearchSourceBuilderException("field must be set on date histogram facet for facet [" + name + "]");
        }
        if (interval == null) {
            throw new SearchSourceBuilderException("interval must be set on date histogram facet for facet [" + name + "]");
        }
        builder.startObject(name);

        builder.startObject(DateHistogramFacet.TYPE);
        if (valueFieldName != null) {
            builder.field("key_field", keyFieldName);
            builder.field("value_field", valueFieldName);
        } else {
            builder.field("field", keyFieldName);
        }
        if (valueScript != null) {
            builder.field("value_script", valueScript);
            if (lang != null) {
                builder.field("lang", lang);
            }
            if (this.params != null) {
                builder.field("params", this.params);
            }
        }
        builder.field("interval", interval);
        if (zone != null) {
            builder.field("time_zone", zone);
        }
        if (comparatorType != null) {
            builder.field("comparator", comparatorType.description());
        }
        builder.endObject();

        addFilterFacetAndGlobal(builder, params);

        builder.endObject();
        return builder;
    }
}
