package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.spring.Profiled;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.matcher.Matcher;
import org.elasticsearch.common.inject.matcher.Matchers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.robin.RobinEngine;
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
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("create", Engine.Create.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("index", Engine.Index.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("flush", Engine.Flush.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("delete", Engine.Delete.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("delete", Engine.DeleteByQuery.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("optimize", Engine.Optimize.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("refresh", Engine.Refresh.class));
            bindEngineMethodToCounterAndAction(parfaitService, RobinEngine.class.getMethod("snapshot", Engine.SnapshotHandler.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    private void bindEngineMethodToCounterAndAction(ParfaitService parfaitService, Method method) {
        String methodName = method.getName();
        bindEngineMethodToCounterAndAction(parfaitService, method, String.format("elasticsearch.index.%s.count", methodName), StringUtils.capitalize(methodName) + " Index Operations");

    }

    private void bindEngineMethodToCounterAndAction(ParfaitService parfaitService, Method method, String counterName, String counterDescription) {
        bindEngineMethodToCounterAndAction(parfaitService, Matchers.only(method), counterName, counterDescription, ParfaitService.INDEX_EVENT_GROUP, method.getName());
    }

    private void bindEngineMethodToCounterAndAction(ParfaitService parfaitService, Matcher<Object> methodMatcher, String counterName, String counterDescription, String eventGroup, String action) {
        Class<Engine> clazz = Engine.class;
        bindClassMethodToCounterAndAction(parfaitService, clazz, methodMatcher, counterName, counterDescription, eventGroup, action);
    }

    private void bindClassMethodToCounterAndAction(ParfaitService parfaitService, Class<?> clazz, Matcher<Object> methodMatcher, String counterName, String counterDescription, String eventGroup, String action) {
        bindInterceptor(Matchers.subclassesOf(clazz), methodMatcher, newProfiledMethodCounter(parfaitService, counterName, counterDescription, eventGroup, action));
    }

    private ProfiledMethodCounter newProfiledMethodCounter(ParfaitService parfaitService, String name, String description, String eventGroup, String action) {
        return new ProfiledMethodCounter(parfaitService.getEventTimer().getCollector(), parfaitService.createMoniteredCounter(name, description), eventGroup, action);
    }
}
