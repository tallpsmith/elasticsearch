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

package org.elasticsearch.node.internal;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.Names;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.FailedToResolveConfigException;

import static org.elasticsearch.common.Strings.*;
import static org.elasticsearch.common.settings.ImmutableSettings.*;

/**
 * @author kimchy (shay.banon)
 */
public class InternalSettingsPerparer {

    public static Tuple<Settings, Environment> prepareSettings(Settings pSettings, boolean loadConfigSettings) {
        // just create enough settings to build the environment
        ImmutableSettings.Builder settingsBuilder = settingsBuilder()
                .put(pSettings)
                .putProperties("elasticsearch.", System.getProperties())
                .putProperties("es.", System.getProperties())
                .replacePropertyPlaceholders();

        Environment environment = new Environment(settingsBuilder.build());

        if (loadConfigSettings) {
            boolean explicitSettingsProvided = false;
            if (System.getProperty("es.config") != null) {
                explicitSettingsProvided = true;
                settingsBuilder.loadFromUrl(environment.resolveConfig(System.getProperty("es.config")));
            }
            if (System.getProperty("elasticsearch.config") != null) {
                explicitSettingsProvided = true;
                settingsBuilder.loadFromUrl(environment.resolveConfig(System.getProperty("elasticsearch.config")));
            }
            if (!explicitSettingsProvided) {
                try {
                    settingsBuilder.loadFromUrl(environment.resolveConfig("elasticsearch.yml"));
                } catch (FailedToResolveConfigException e) {
                    // ignore
                } catch (NoClassDefFoundError e) {
                    // ignore, no yaml
                }
                try {
                    settingsBuilder.loadFromUrl(environment.resolveConfig("elasticsearch.json"));
                } catch (FailedToResolveConfigException e) {
                    // ignore
                }
                try {
                    settingsBuilder.loadFromUrl(environment.resolveConfig("elasticsearch.properties"));
                } catch (FailedToResolveConfigException e) {
                    // ignore
                }
            }
        }

        settingsBuilder.put(pSettings)
                .putProperties("elasticsearch.", System.getProperties())
                .putProperties("es.", System.getProperties())
                .replacePropertyPlaceholders();

        // generate the name
        if (settingsBuilder.get("name") == null) {
            String name = System.getProperty("name");
            if (name == null || name.isEmpty())
                name = Names.randomNodeName(environment.resolveConfig("names.txt"));

            if (name != null) {
                settingsBuilder.put("name", name);
            }
        }

        // put the cluster name
        if (settingsBuilder.get(ClusterName.SETTING) == null) {
            settingsBuilder.put(ClusterName.SETTING, ClusterName.DEFAULT.value());
        }

        Settings v1 = settingsBuilder.build();
        environment = new Environment(v1);

        // put back the env settings
        settingsBuilder = settingsBuilder().put(v1);
        settingsBuilder.put("path.home", cleanPath(environment.homeFile().getAbsolutePath()));
        settingsBuilder.put("path.work", cleanPath(environment.workFile().getAbsolutePath()));
        settingsBuilder.put("path.work_with_cluster", cleanPath(environment.workWithClusterFile().getAbsolutePath()));
        settingsBuilder.put("path.data", cleanPath(environment.dataFile().getAbsolutePath()));
        settingsBuilder.put("path.data_with_cluster", cleanPath(environment.dataWithClusterFile().getAbsolutePath()));
        settingsBuilder.put("path.logs", cleanPath(environment.logsFile().getAbsolutePath()));

        v1 = settingsBuilder.build();

        return new Tuple<Settings, Environment>(v1, environment);
    }
}
