package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.spring.Profiled;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.matcher.Matchers;
import org.elasticsearch.common.settings.Settings;
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
    }

    private ProfiledMethodCounter newProfiledMethodCounter(ParfaitService parfaitService, String name, String description, String group) {
        return new ProfiledMethodCounter(parfaitService.getEventTimer().getCollector(), parfaitService.createMoniteredCounter(name, description), group);
    }

}
