package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.*;
import com.custardsource.parfait.dxm.IdentifierSourceSet;
import com.custardsource.parfait.dxm.PcpMmvWriter;
import com.custardsource.parfait.io.ByteCountingInputStream;
import com.custardsource.parfait.io.ByteCountingOutputStream;
import com.custardsource.parfait.jmx.JmxView;
import com.custardsource.parfait.pcp.*;
import com.custardsource.parfait.spring.SelfStartingMonitoringView;
import com.custardsource.parfait.timing.EventTimer;
import com.custardsource.parfait.timing.LoggerSink;
import com.custardsource.parfait.timing.StepMeasurementSink;
import com.custardsource.parfait.timing.ThreadMetricSuite;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.shard.ShardId;

import javax.measure.unit.SI;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ParfaitService extends AbstractLifecycleComponent<Void> {

    public static final String SEARCH_EVENT_GROUP = "search" ;
    public static final String INDEX_EVENT_GROUP = "index";

    private static final List<String> EVENT_GROUPS = Arrays.asList(INDEX_EVENT_GROUP, SEARCH_EVENT_GROUP);

    private final MonitorableRegistry monitorableRegistry;
    private final SelfStartingMonitoringView selfStartingMonitoringView;

    private static final int ELASTICSEARCH_PCP_CLUSTER_IDENTIFIER = 0xB01; /*NG BO1NG - funny... ok you had to be there*/
    private final EventTimer eventTimer;

    public ParfaitService(Settings settings) {
        super(settings);
        monitorableRegistry = new MonitorableRegistry();
        Boolean isData = settings.getAsBoolean("node.data", false);
        Boolean isClient = settings.getAsBoolean("node.client", false);
        boolean isServer = isData || !isClient;

        String nodeType = isServer ? "server" : "client";

        // TODO remove this debug rubbish
        System.out.printf("isData=%s, isClient=%s, isServer=%s, nodeType=%s\n", isData, isClient, isServer, nodeType);

        final PcpMmvWriter mmvWriter = new PcpMmvWriter("elasticsearch-" + nodeType + ".mmv", IdentifierSourceSet.DEFAULT_SET);
        mmvWriter.setClusterIdentifier(ELASTICSEARCH_PCP_CLUSTER_IDENTIFIER);

        final PcpMonitorBridge pcpMonitorBridge = new PcpMonitorBridge(mmvWriter, MetricNameMapper.PASSTHROUGH_MAPPER, new MetricDescriptionTextSource(), new EmptyTextSource());

        // TODO whoops, forgot that JmxView relies on the Spring @ManagedResource stuff to expose, so need to get passed in the JmxService
        final JmxView jmxView = new JmxView();
        final CompositeMonitoringView compositeMonitoringView = new CompositeMonitoringView(pcpMonitorBridge, jmxView);
        selfStartingMonitoringView = new SelfStartingMonitoringView(monitorableRegistry, compositeMonitoringView, 2000);

        LoggerSink loggerSink = new LoggerSink(getClass().getSimpleName());
        loggerSink.normalizeUnits(SI.NANO(SI.SECOND), SI.MILLI(SI.SECOND));

        List<StepMeasurementSink> sinks = Collections.<StepMeasurementSink>singletonList(loggerSink);
        boolean enableCpuCollection = true;
        boolean enableContentionCollection = false;
        eventTimer = new EventTimer("elasticsearch", monitorableRegistry, ThreadMetricSuite.withDefaultMetrics(), enableCpuCollection, enableContentionCollection, sinks);

        for (String eventGroup: EVENT_GROUPS) {
            eventTimer.registerMetric(eventGroup);
        }
    }


    public Monitorable<?> createMoniteredLongValue(String name, String description, Long initialValue) {
        return register(new MonitoredLongValue(name, description, null, initialValue));
    }

    public MonitoredCounter createMoniteredCounter(String name, String description) {
        return register(new MonitoredCounter(name, description, (MonitorableRegistry) null));
    }


    @SuppressWarnings("unchecked")
    private <T extends Monitorable> T register(T monitorable) {
        // TODO need to check if the monitorable exits and resue it, this is particularly noticeable if Indexes are deleted and recreated
        // need to merge in Cowan's latest changes to see this though
        return (T) monitorableRegistry.registerOrReuse(monitorable);
    }

    public ByteCountingOutputStream wrapAsCountingOutputStream(OutputStream out, Counter existingCounter) {
        return new ByteCountingOutputStream(out, existingCounter);
    }

    public ByteCountingInputStream wrapAsCountingInputStream(InputStream is, Counter existingCounter) {
        return new ByteCountingInputStream(is, existingCounter);
    }

    @Override protected void doStart() {
        selfStartingMonitoringView.start();
    }

    @Override protected void doStop() {
        selfStartingMonitoringView.stop();
    }

    @Override protected void doClose() {
    }

    public MonitoredCounterBuilder forShard(ShardId shardId) {
        return new MonitoredCounterBuilder(shardId);
    }

    public final class MonitoredCounterBuilder {


        private final ShardId shardId;

        public MonitoredCounterBuilder(ShardId shardId) {
            this.shardId = shardId;
        }


        public MonitoredCounter count(String op) {
            return createMoniteredCounter(String.format("elasticsearch.index[%s/%s].%s.count", shardId.getIndex(), shardId.id(), op), String.format("# %s Operations performed by the engine for a given shard", StringUtils.capitalize(op)));
        }
    }

    public EventTimer getEventTimer() {
        return eventTimer;
    }


}
