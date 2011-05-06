package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.spring.Profiled;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.matcher.Matchers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.robin.RobinEngine;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.search.query.QueryPhase;

public class ParfaitModule extends AbstractModule {
    private final Settings settings;

    public ParfaitModule(Settings settings) {
        this.settings = settings;

    }

    @Override protected void configure() {
        final ParfaitService parfaitService = new ParfaitService(settings);
        bind(ParfaitService.class).toInstance(parfaitService);

        bindInterceptor(Matchers.subclassesOf(QueryPhase.class), Matchers.annotatedWith(Profiled.class),
                newProfiledMethodCounter(parfaitService, "elasticsearch.search.query.count", "Search Query phase counter", "query"));

        // TODO need to differentiate the other methods, this is hard coded to the Delete op
        // tODO the annotations need to be on the subclass, and not the Engine class for some reason, need to understand why
        // TODO the log output has "elasticsearch:index" as the op name, that clearly should be "index:delete" (same for search, "search:query" and "search:fetch" etc..
        bindInterceptor(Matchers.subclassesOf(Engine.class), Matchers.annotatedWith(Profiled.class), newProfiledMethodCounter(parfaitService, "elasticsearch.index.delete.count", "Delete Index Operations", "index"));
    }

    private ProfiledMethodCounter newProfiledMethodCounter(ParfaitService parfaitService, String name, String description, String group) {
        return new ProfiledMethodCounter(parfaitService.getEventTimer().getCollector(), parfaitService.createMoniteredCounter(name, description), group);
    }

}
