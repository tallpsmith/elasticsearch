package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.Counter;
import com.custardsource.parfait.MonitorableRegistry;
import com.custardsource.parfait.MonitoredLongValue;
import com.custardsource.parfait.dxm.FileParsingIdentifierSourceSet;
import com.custardsource.parfait.dxm.IdentifierSourceSet;
import com.custardsource.parfait.dxm.PcpMmvWriter;
import com.custardsource.parfait.io.ByteCountingInputStream;
import com.custardsource.parfait.io.ByteCountingOutputStream;
import com.custardsource.parfait.pcp.PcpMonitorBridge;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.settings.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;


public class ParfaitService extends AbstractLifecycleComponent<Void> {
    private final MonitorableRegistry monitorableRegistry;
    private final PcpMonitorBridge pcpMonitorBridge;

    public ParfaitService(Settings settings) {
        super(settings);
        monitorableRegistry = new MonitorableRegistry();
        // TODO metricsources etc
        // TODO use different mmv writer name based on cluster name
        Reader instanceData = new StringReader(""); // TODO empty for now, but could have different instance dimensions for different types of operations
        Reader metricData = new StringReader("elasticsearch.index.bulk  1"); // TODO, put this into a resource file

        IdentifierSourceSet fallbacks = IdentifierSourceSet.EXPLICIT_SET;
        IdentifierSourceSet identifierSourceSet = new FileParsingIdentifierSourceSet(instanceData, metricData, fallbacks);
        pcpMonitorBridge = new PcpMonitorBridge(new PcpMmvWriter("elasticSearch", identifierSourceSet), monitorableRegistry);
    }


    public MonitoredLongValue createMoniteredLongValue(String name, String description, Long initialValue) {
        return new MonitoredLongValue(name, description, initialValue);
    }

    public ByteCountingOutputStream wrapAsCountingOutputStream(OutputStream out, Counter existingCounter) {
        return new ByteCountingOutputStream(out, existingCounter);
    }

    public ByteCountingInputStream wrapAsCountingInputStream(InputStream is, Counter existingCounter) {
        return new ByteCountingInputStream(is, existingCounter);
    }

    @Override protected void doStart() {
        monitorableRegistry.freeze();
        pcpMonitorBridge.start();
    }

    @Override protected void doStop() {
        pcpMonitorBridge.stop();
    }

    @Override protected void doClose() {
    }

}
