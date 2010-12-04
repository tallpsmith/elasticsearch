package org.elasticsearch.monitor.parfait;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;

public class ParfaitModule extends AbstractModule {
    private final Settings settings;


    public ParfaitModule(Settings settings) {
        this.settings = settings;

    }

    @Override protected void configure() {

        final ParfaitService  parfaitService = new ParfaitService(settings);
        bind(ParfaitService.class).toInstance(parfaitService);
    }


}
