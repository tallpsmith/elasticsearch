package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.spring.Profiled;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.matcher.Matcher;
import org.elasticsearch.common.inject.matcher.Matchers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.robin.RobinEngine;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.search.dfs.DfsPhase;
import org.elasticsearch.search.facet.FacetPhase;
import org.elasticsearch.search.fetch.FetchPhase;
import org.elasticsearch.search.query.QueryPhase;

import java.lang.reflect.Method;

import static org.hamcrest.core.AllOf.allOf;

public class ParfaitModule extends AbstractModule {
    private final Settings settings;

    public ParfaitModule(Settings settings) {
        this.settings = settings;

    }

    @Override protected void configure() {
        final ParfaitService parfaitService = new ParfaitService(settings);
        bind(ParfaitService.class).toInstance(parfaitService);

        // TODO these eventGroups shoud be given to ParfaitService to register, it shouldn't really have the knowledge
        bindInterceptor(Matchers.subclassesOf(QueryPhase.class), Matchers.annotatedWith(Profiled.class),
                newProfiledMethodCounter(parfaitService, "elasticsearch.search.query.count", "Search Query phase counter", ParfaitService.SEARCH_EVENT_GROUP, "query"));
        bindInterceptor(Matchers.subclassesOf(FetchPhase.class), Matchers.annotatedWith(Profiled.class),
                newProfiledMethodCounter(parfaitService, "elasticsearch.search.fetch.count", "Search Fetch phase counter", ParfaitService.SEARCH_EVENT_GROUP, "fetch"));
        bindInterceptor(Matchers.subclassesOf(FacetPhase.class), Matchers.annotatedWith(Profiled.class),
                newProfiledMethodCounter(parfaitService, "elasticsearch.search.facet.count", "Search Facet phase counter", ParfaitService.SEARCH_EVENT_GROUP, "facet"));
        bindInterceptor(Matchers.subclassesOf(DfsPhase.class), Matchers.annotatedWith(Profiled.class),
                newProfiledMethodCounter(parfaitService, "elasticsearch.search.dfs.count", "Search DFS phase counter", ParfaitService.SEARCH_EVENT_GROUP, "dfs"));


        // TODO need to differentiate the other methods, this is hard coded to the Delete op
        // tODO the annotations need to be on the subclass, and not the Engine class for some reason, need to understand why
        // TODO the log output has "elasticsearch:index" as the op name, that clearly should be "index:delete" (same for search, "search:query" and "search:fetch" etc..
        try {
            Method delete = RobinEngine.class.getMethod("delete", Engine.Delete.class);
            Matcher<Object> deleteMethodMatcher = Matchers.only(delete);
            bindInterceptor(Matchers.subclassesOf(Engine.class), deleteMethodMatcher, newProfiledMethodCounter(parfaitService, "elasticsearch.index.delete.count", "Delete Index Operations", ParfaitService.INDEX_EVENT_GROUP, "delete"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private ProfiledMethodCounter newProfiledMethodCounter(ParfaitService parfaitService, String name, String description, String eventGroup, String action) {
        return new ProfiledMethodCounter(parfaitService.getEventTimer().getCollector(), parfaitService.createMoniteredCounter(name, description), eventGroup, action);
    }

}
