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

package org.elasticsearch.script;

import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.collect.MapMaker;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.cache.field.data.FieldDataCache;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.script.mvel.MvelScriptEngineService;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author kimchy (shay.banon)
 */
public class ScriptService extends AbstractComponent {

    private final String defaultLang;

    private final ImmutableMap<String, ScriptEngineService> scriptEngines;

    private final ConcurrentMap<String, CompiledScript> staticCache = ConcurrentCollections.newConcurrentMap();

    private final ConcurrentMap<String, CompiledScript> cache = new MapMaker().softValues().makeMap();

    public ScriptService(Settings settings) {
        this(settings, new Environment(), ImmutableSet.<ScriptEngineService>builder()
                .add(new MvelScriptEngineService(settings))
                .build()
        );
    }

    @Inject public ScriptService(Settings settings, Environment env, Set<ScriptEngineService> scriptEngines) {
        super(settings);

        this.defaultLang = componentSettings.get("default_lang", "mvel");

        ImmutableMap.Builder<String, ScriptEngineService> builder = ImmutableMap.builder();
        for (ScriptEngineService scriptEngine : scriptEngines) {
            for (String type : scriptEngine.types()) {
                builder.put(type, scriptEngine);
            }
        }
        this.scriptEngines = builder.build();

        // compile static scripts
        File scriptsFile = new File(env.configFile(), "scripts");
        if (scriptsFile.exists()) {
            processScriptsDirectory("", scriptsFile);
        }
    }

    private void processScriptsDirectory(String prefix, File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                processScriptsDirectory(prefix + file.getName() + "_", file);
            } else {
                int extIndex = file.getName().lastIndexOf('.');
                if (extIndex != -1) {
                    String ext = file.getName().substring(extIndex + 1);
                    String scriptName = prefix + file.getName().substring(0, extIndex);
                    boolean found = false;
                    for (ScriptEngineService engineService : scriptEngines.values()) {
                        for (String s : engineService.extensions()) {
                            if (s.equals(ext)) {
                                found = true;
                                try {
                                    String script = Streams.copyToString(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                                    staticCache.put(scriptName, new CompiledScript(engineService.types()[0], engineService.compile(script)));
                                } catch (Exception e) {
                                    logger.warn("failed to load/compile script [{}]", e, scriptName);
                                }
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                    if (!found) {
                        logger.warn("no script engine found for [{}]", ext);
                    }
                }
            }
        }
    }

    public void close() {
        for (ScriptEngineService engineService : scriptEngines.values()) {
            engineService.close();
        }
    }

    public CompiledScript compile(String script) {
        return compile(defaultLang, script);
    }

    public CompiledScript compile(String lang, String script) {
        CompiledScript compiled = staticCache.get(script);
        if (compiled != null) {
            return compiled;
        }
        compiled = cache.get(script);
        if (compiled != null) {
            return compiled;
        }
        if (lang == null) {
            lang = defaultLang;
        }
        synchronized (cache) {
            compiled = cache.get(script);
            if (compiled != null) {
                return compiled;
            }
            ScriptEngineService service = scriptEngines.get(lang);
            if (service == null) {
                throw new ElasticSearchIllegalArgumentException("script_lang not supported [" + lang + "]");
            }
            compiled = new CompiledScript(lang, service.compile(script));
            cache.put(script, compiled);
        }
        return compiled;
    }

    public ExecutableScript executable(String lang, String script, Map vars) {
        return executable(compile(lang, script), vars);
    }

    public ExecutableScript executable(CompiledScript compiledScript, Map vars) {
        return scriptEngines.get(compiledScript.lang()).executable(compiledScript.compiled(), vars);
    }

    public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, @Nullable Map<String, Object> vars) {
        return scriptEngines.get(compiledScript.lang()).search(compiledScript.compiled(), lookup, vars);
    }

    public SearchScript search(SearchLookup lookup, String lang, String script, @Nullable Map<String, Object> vars) {
        return search(compile(lang, script), lookup, vars);
    }

    public SearchScript search(MapperService mapperService, FieldDataCache fieldDataCache, String lang, String script, @Nullable Map<String, Object> vars) {
        return search(compile(lang, script), new SearchLookup(mapperService, fieldDataCache), vars);
    }

    public Object execute(CompiledScript compiledScript, Map vars) {
        return scriptEngines.get(compiledScript.lang()).execute(compiledScript.compiled(), vars);
    }

    public void clear() {
        cache.clear();
    }

}
