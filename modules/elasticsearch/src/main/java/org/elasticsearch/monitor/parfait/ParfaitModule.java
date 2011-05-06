package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.spring.Profiled;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.aopalliance.intercept.MethodInterceptor;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.matcher.Matcher;
import org.elasticsearch.common.inject.matcher.Matchers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.robin.RobinEngine;
import org.elasticsearch.search.SearchPhase;
import org.elasticsearch.search.dfs.DfsPhase;
import org.elasticsearch.search.facet.FacetPhase;
import org.elasticsearch.search.fetch.FetchPhase;
import org.elasticsearch.search.query.QueryPhase;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ParfaitModule extends AbstractModule {
    private final Settings settings;

    public ParfaitModule(Settings settings) {
        this.settings = settings;

    }

    @Override protected void configure() {
        final ParfaitService parfaitService = new ParfaitService(settings);
        final ParfaitJvmService parfaitJvmService = new ParfaitJvmService(settings, parfaitService);

        bind(ParfaitService.class).toInstance(parfaitService);

        List<Class<? extends SearchPhase>> searchPhasesToInstrument = Arrays.asList(QueryPhase.class, FetchPhase.class, FacetPhase.class, DfsPhase.class);

        for (Class searchPhase: searchPhasesToInstrument) {
            bindSearchPhaseExecutionToCounterAndAction(parfaitService, searchPhase);
        }

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

    private void bindSearchPhaseExecutionToCounterAndAction(ParfaitService parfaitService, Class<QueryPhase> queryPhaseClass) {
        String name = nameForSearchPhase(queryPhaseClass);
        bindInterceptor(Matchers.subclassesOf(queryPhaseClass), Matchers.annotatedWith(Profiled.class),
                newProfiledMethodCounter(parfaitService, String.format("elasticsearch.search.%s.count", name), String.format("Search %s phase counter", StringUtils.capitalize(name)), ParfaitService.SEARCH_EVENT_GROUP, name));
    }

    private MethodInterceptor newProfiledMethodCounter(ParfaitService parfaitService, String counterName, String counterDescription, String eventGroup, String action) {
        return new ProfiledMethodCounter(parfaitService.getEventTimer().getCollector(), parfaitService.createMoniteredCounter(counterName, counterDescription), eventGroup, action);
    }

    private String nameForSearchPhase(Class<QueryPhase> queryPhaseClass) {
        return StringUtils.removeEnd(StringUtils.lowerCase(queryPhaseClass.getSimpleName()), "phase");
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
        bindClassMethodToCounterAndAction(parfaitService, clazz, methodMatcher, eventGroup, action);
    }

    private void bindClassMethodToCounterAndAction(ParfaitService parfaitService, Class<?> clazz, Matcher<Object> methodMatcher, String eventGroup, String action) {
        bindInterceptor(Matchers.subclassesOf(clazz), methodMatcher, newProfiledShardBasedMethodCounter(parfaitService, eventGroup, action));
    }

    private ProfiledShardBasedMethodCounter newProfiledShardBasedMethodCounter(ParfaitService parfaitService, String eventGroup, String action) {
        return new ProfiledShardBasedMethodCounter(parfaitService, eventGroup, action);
    }
}
