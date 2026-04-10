package io.cresco.proxyshield;

import com.google.gson.Gson;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import java.util.Map;

/**
 * Executor for handling Cresco messages for the Proxy Shield Manager plugin.
 */
public class PluginExecutor implements Executor {

    private final PluginBuilder plugin;
    private final CLogger logger;
    private final Gson gson;
    private final ProxyShieldController proxyShieldController;

    public PluginExecutor(PluginBuilder pluginBuilder, ProxyShieldController proxyShieldController) {
        this.plugin = pluginBuilder;
        this.logger = plugin.getLogger(PluginExecutor.class.getName(), CLogger.Level.Info);
        this.proxyShieldController = proxyShieldController;
        this.gson = new Gson();
    }

    @Override
    public MsgEvent executeCONFIG(MsgEvent incoming) {
        logger.debug("Processing CONFIG message: Action = " + incoming.getParam("action"));

        if (incoming.getParams().containsKey("action")) {
            String action = incoming.getParam("action");
            try {
                switch (action) {
                    case "updatetargethost":
                        return updateTargetHost(incoming);
                    case "restartproxyshield":
                        return restartProxyShield(incoming);
                    default:
                        logger.error("Unknown/Unsupported CONFIG action: {}", action);
                        incoming.setParam("status", "99");
                        incoming.setParam("status_desc", "Unknown/Unsupported config action: " + action);
                        break;
                }
            } catch (Exception e) {
                logger.error("Error processing CONFIG action '" + action + "': " + e.getMessage(), e);
                incoming.setParam("status", "500");
                incoming.setParam("status_desc",
                        "Internal error processing action '" + action + "': " + e.getMessage());
            }
        } else {
            logger.error("CONFIG message received without 'action' parameter.");
            incoming.setParam("status", "400");
            incoming.setParam("status_desc", "Missing 'action' parameter in CONFIG message.");
        }
        return incoming;
    }

    @Override
    public MsgEvent executeEXEC(MsgEvent incoming) {
        logger.debug("Processing EXEC message: Action = " + incoming.getParam("action"));
        if (incoming.getParams().containsKey("action")) {
            String action = incoming.getParam("action");
            try {
                switch (action) {
                    case "getstatus":
                        return getStatus(incoming);
                    case "gettargethost":
                        return getTargetHost(incoming);
                    case "updateandrestart":
                        return updateAndRestart(incoming);
                    default:
                        logger.error("Unknown/Unsupported EXEC action: {}", action);
                        incoming.setParam("status", "99");
                        incoming.setParam("status_desc", "Unknown/Unsupported exec action: " + action);
                        break;
                }
            } catch (Exception e) {
                logger.error("Error processing EXEC action '" + action + "': " + e.getMessage(), e);
                incoming.setParam("status", "500");
                incoming.setParam("status_desc",
                        "Internal error processing action '" + action + "': " + e.getMessage());
            }
        } else {
            logger.error("EXEC message received without 'action' parameter.");
            incoming.setParam("status", "400");
            incoming.setParam("status_desc", "Missing 'action' parameter in EXEC message.");
        }
        return incoming;
    }

    /**
     * Update the TARGET_HOST value in the .env file.
     */
    private MsgEvent updateTargetHost(MsgEvent incoming) {
        logger.info("Handling updatetargethost request...");
        try {
            String newTargetHost = incoming.getParam("target_host");
            if (newTargetHost != null && !newTargetHost.trim().isEmpty()) {
                boolean success = proxyShieldController.updateTargetHost(newTargetHost.trim());
                if (success) {
                    incoming.setParam("status", "10");
                    incoming.setParam("status_desc", "TARGET_HOST updated successfully to: " + newTargetHost);
                    incoming.setParam("target_host", newTargetHost);
                } else {
                    incoming.setParam("status", "9");
                    incoming.setParam("status_desc", "Failed to update TARGET_HOST (check logs for details).");
                }
            } else {
                logger.error("Missing 'target_host' parameter for updatetargethost.");
                incoming.setParam("status", "400");
                incoming.setParam("status_desc", "Missing required parameter: target_host");
            }
        } catch (Exception e) {
            logger.error("Error during updatetargethost processing", e);
            incoming.setParam("status", "500");
            incoming.setParam("status_desc", "Internal error: " + e.getMessage());
        }
        return incoming;
    }

    /**
     * Restart the proxy-shield docker compose project.
     */
    private MsgEvent restartProxyShield(MsgEvent incoming) {
        logger.info("Handling restartproxyshield request...");
        try {
            boolean success = proxyShieldController.restartDockerCompose();
            if (success) {
                incoming.setParam("status", "10");
                incoming.setParam("status_desc", "Proxy shield restarted successfully.");
            } else {
                incoming.setParam("status", "9");
                incoming.setParam("status_desc", "Failed to restart proxy shield (check logs for details).");
            }
        } catch (Exception e) {
            logger.error("Error during restartproxyshield processing", e);
            incoming.setParam("status", "500");
            incoming.setParam("status_desc", "Internal error: " + e.getMessage());
        }
        return incoming;
    }

    /**
     * Update TARGET_HOST and restart docker compose.
     */
    private MsgEvent updateAndRestart(MsgEvent incoming) {
        logger.info("Handling updateandrestart request...");
        try {
            String newTargetHost = incoming.getParam("target_host");
            if (newTargetHost != null && !newTargetHost.trim().isEmpty()) {
                boolean success = proxyShieldController.updateAndRestart(newTargetHost.trim());
                if (success) {
                    incoming.setParam("status", "10");
                    incoming.setParam("status_desc", "TARGET_HOST updated and proxy shield restarted successfully.");
                    incoming.setParam("target_host", newTargetHost);
                } else {
                    incoming.setParam("status", "9");
                    incoming.setParam("status_desc",
                            "Failed to update TARGET_HOST and/or restart proxy shield (check logs for details).");
                }
            } else {
                logger.error("Missing 'target_host' parameter for updateandrestart.");
                incoming.setParam("status", "400");
                incoming.setParam("status_desc", "Missing required parameter: target_host");
            }
        } catch (Exception e) {
            logger.error("Error during updateandrestart processing", e);
            incoming.setParam("status", "500");
            incoming.setParam("status_desc", "Internal error: " + e.getMessage());
        }
        return incoming;
    }

    /**
     * Get the current status of the proxy-shield project.
     */
    private MsgEvent getStatus(MsgEvent incoming) {
        logger.info("Handling getstatus request...");
        try {
            Map<String, String> status = proxyShieldController.getStatus();
            incoming.setParam("status_info", gson.toJson(status));
            incoming.setParam("status", "10");
            incoming.setParam("status_desc", "Successfully retrieved proxy shield status.");
        } catch (Exception e) {
            logger.error("Error during getstatus processing", e);
            incoming.setParam("status", "500");
            incoming.setParam("status_desc", "Internal error: " + e.getMessage());
        }
        return incoming;
    }

    /**
     * Get the current TARGET_HOST value.
     */
    private MsgEvent getTargetHost(MsgEvent incoming) {
        logger.info("Handling gettargethost request...");
        try {
            String targetHost = proxyShieldController.getTargetHost();
            if (targetHost != null) {
                incoming.setParam("target_host", targetHost);
                incoming.setParam("status", "10");
                incoming.setParam("status_desc", "Successfully retrieved TARGET_HOST.");
            } else {
                incoming.setParam("status", "9");
                incoming.setParam("status_desc", "TARGET_HOST not found in .env file.");
            }
        } catch (Exception e) {
            logger.error("Error during gettargethost processing", e);
            incoming.setParam("status", "500");
            incoming.setParam("status_desc", "Internal error: " + e.getMessage());
        }
        return incoming;
    }

    @Override
    public MsgEvent executeDISCOVER(MsgEvent incoming) {
        logger.warn("Received unimplemented DISCOVER message.");
        return null;
    }

    @Override
    public MsgEvent executeERROR(MsgEvent incoming) {
        logger.error("Received ERROR message: " + incoming.getParams());
        return null;
    }

    @Override
    public MsgEvent executeINFO(MsgEvent incoming) {
        logger.info("Received INFO message: " + incoming.getParams());
        return null;
    }

    @Override
    public MsgEvent executeWATCHDOG(MsgEvent incoming) {
        logger.debug("Received WATCHDOG message.");
        return null;
    }

    @Override
    public MsgEvent executeKPI(MsgEvent incoming) {
        logger.debug("Received KPI message.");
        return null;
    }
}
