package io.cresco.proxyshield;

import io.cresco.library.agent.AgentService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.plugin.PluginService;
import io.cresco.library.utilities.CLogger;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component(service = {
        PluginService.class }, scope = ServiceScope.PROTOTYPE, configurationPolicy = ConfigurationPolicy.REQUIRE, reference = @Reference(name = "io.cresco.library.agent.AgentService", service = AgentService.class))
public class Plugin implements PluginService {

    private BundleContext context;
    private PluginBuilder pluginBuilder;
    private Executor executor;
    private CLogger logger;
    private Map<String, Object> map;
    private ProxyShieldController proxyShieldController;

    private final AtomicBoolean isActive = new AtomicBoolean(false);

    @Activate
    void activate(BundleContext context, Map<String, Object> map) {
        this.context = context;
        this.map = map;
    }

    @Modified
    void modified(BundleContext context, Map<String, Object> map) {
        if (logger != null) {
            logger.info("Configuration modified.");
        } else {
            System.out.println("Plugin configuration modified but logger not yet initialized.");
        }
    }

    @Deactivate
    void deactivate(BundleContext context, Map<String, Object> map) {
        isStopped();
        this.context = null;
        this.map = null;
        this.pluginBuilder = null;
        this.executor = null;
        this.proxyShieldController = null;
        this.logger = null;
        System.out.println("Proxy Shield Manager Plugin Deactivated.");
    }

    @Override
    public boolean isActive() {
        return this.isActive.get();
    }

    @Override
    public void setIsActive(boolean isActive) {
        this.isActive.set(isActive);
    }

    @Override
    public boolean inMsg(MsgEvent incoming) {
        if (pluginBuilder != null) {
            pluginBuilder.msgIn(incoming);
            return true;
        }
        return false;
    }

    @Override
    public boolean isStarted() {
        try {
            if (pluginBuilder == null) {
                pluginBuilder = new PluginBuilder(this.getClass().getName(), context, map);
                this.logger = pluginBuilder.getLogger(Plugin.class.getName(), CLogger.Level.Info);
                logger.info("Initializing Proxy Shield Manager Plugin...");

                this.proxyShieldController = new ProxyShieldController(pluginBuilder);
                logger.info("ProxyShieldController initialized.");

                this.executor = new PluginExecutor(pluginBuilder, proxyShieldController);
                pluginBuilder.setExecutor(executor);
                logger.info("PluginExecutor initialized and set.");

                while (!pluginBuilder.getAgentService().getAgentState().isActive()) {
                    logger.info("Plugin " + pluginBuilder.getPluginID() + " waiting for Agent to become active...");
                    Thread.sleep(1000);
                }
                logger.info("Agent is active. Plugin startup complete.");

                this.isActive.set(true);
            }
            return this.isActive.get();
        } catch (Exception ex) {
            if (logger != null) {
                logger.error("Proxy Shield Manager Plugin startup failed: " + ex.getMessage(), ex);
            } else {
                System.err.println(
                        "Proxy Shield Manager Plugin startup failed (logger not initialized): " + ex.getMessage());
                ex.printStackTrace();
            }
            isStopped();
            return false;
        }
    }

    @Override
    public boolean isStopped() {
        this.isActive.set(false);

        if (proxyShieldController != null) {
            proxyShieldController.shutdown();
        }

        if (pluginBuilder != null) {
            pluginBuilder.setExecutor(null);
        }

        return true;
    }
}
