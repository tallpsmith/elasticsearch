package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.MonitoredCounter;
import com.custardsource.parfait.spring.Profiled;
import org.elasticsearch.common.aopalliance.intercept.MethodInterceptor;
import org.elasticsearch.common.aopalliance.intercept.MethodInvocation;
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

        final ParfaitService  parfaitService = new ParfaitService(settings);
        bind(ParfaitService.class).toInstance(parfaitService);

        bindInterceptor(Matchers.subclassesOf(QueryPhase.class), Matchers.annotatedWith(Profiled.class), new MethodInterceptor() {

            private MonitoredCounter queryPhaseCounter = parfaitService.createMoniteredCounter("elasticsearch.search.query.count", "Search Query phase counter");

            @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
                parfaitService.getEventTimer().getCollector().startTiming(ParfaitService.ELASTICSEARCH_EVENT_GROUP, "query");
                try {
                    queryPhaseCounter.inc();
                    return methodInvocation.proceed();
                } finally {
                    parfaitService.getEventTimer().getCollector().stopTiming();
                }
            }
        });
    }


}
