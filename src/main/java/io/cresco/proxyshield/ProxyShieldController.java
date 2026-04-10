package io.cresco.proxyshield;

import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Controller for managing the proxy-shield docker compose project.
 * Handles .env file modifications and docker compose lifecycle.
 */
public class ProxyShieldController {

    private final PluginBuilder plugin;
    private final CLogger logger;

    // Configuration
    private String proxyShieldPath;
    private String envFilePath;

    // Default path - can be overridden via configuration
    private static final String DEFAULT_PROXY_SHIELD_PATH = "./proxy-shield";
    private static final String ENV_FILE_NAME = ".env";
    private static final String TARGET_HOST_KEY = "TARGET_HOST";

    public ProxyShieldController(PluginBuilder plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger(this.getClass().getName(), CLogger.Level.Info);

        // Get proxy-shield path from configuration or use default
        this.proxyShieldPath = DEFAULT_PROXY_SHIELD_PATH;
        this.envFilePath = Paths.get(proxyShieldPath, ENV_FILE_NAME).toString();

        logger.info("ProxyShieldController initialized with path: " + proxyShieldPath);
    }

    /**
     * Update the TARGET_HOST value in the .env file.
     * 
     * @param newTargetHost The new target host value
     * @return true if successful, false otherwise
     */
    public boolean updateTargetHost(String newTargetHost) {
        if (newTargetHost == null || newTargetHost.trim().isEmpty()) {
            logger.error("Cannot update TARGET_HOST: value is null or empty");
            return false;
        }

        try {
            Path envPath = Paths.get(envFilePath);

            if (!Files.exists(envPath)) {
                logger.error(".env file not found at: " + envFilePath);
                return false;
            }

            // Read all lines from the .env file
            List<String> lines = Files.readAllLines(envPath);
            List<String> updatedLines = new ArrayList<>();
            boolean found = false;

            for (String line : lines) {
                if (line.startsWith(TARGET_HOST_KEY + "=")) {
                    updatedLines.add(TARGET_HOST_KEY + "=" + newTargetHost.trim());
                    found = true;
                    logger.info("Updated TARGET_HOST from '" + line.substring(TARGET_HOST_KEY.length() + 1) +
                            "' to '" + newTargetHost.trim() + "'");
                } else {
                    updatedLines.add(line);
                }
            }

            if (!found) {
                // TARGET_HOST not found, add it
                updatedLines.add(TARGET_HOST_KEY + "=" + newTargetHost.trim());
                logger.info("Added TARGET_HOST=" + newTargetHost.trim() + " to .env file");
            }

            // Write the updated content back to the file
            Files.write(envPath, updatedLines);
            logger.info("Successfully updated .env file at: " + envFilePath);

            return true;

        } catch (IOException e) {
            logger.error("Failed to update .env file: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get the current TARGET_HOST value from the .env file.
     * 
     * @return The current TARGET_HOST value, or null if not found
     */
    public String getTargetHost() {
        try {
            Path envPath = Paths.get(envFilePath);

            if (!Files.exists(envPath)) {
                logger.error(".env file not found at: " + envFilePath);
                return null;
            }

            List<String> lines = Files.readAllLines(envPath);

            for (String line : lines) {
                if (line.startsWith(TARGET_HOST_KEY + "=")) {
                    return line.substring(TARGET_HOST_KEY.length() + 1).trim();
                }
            }

            logger.warn("TARGET_HOST not found in .env file");
            return null;

        } catch (IOException e) {
            logger.error("Failed to read .env file: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Restart the docker compose project.
     * This will stop and start the containers with the new configuration.
     * 
     * @return true if successful, false otherwise
     */
    public boolean restartDockerCompose() {
        logger.info("Restarting docker compose at: " + proxyShieldPath);

        try {
            // First, stop the containers
            ProcessBuilder stopPb = new ProcessBuilder("docker", "compose", "down");
            stopPb.directory(new File(proxyShieldPath));
            stopPb.redirectErrorStream(true);

            Process stopProcess = stopPb.start();
            boolean stopCompleted = stopProcess.waitFor(60, TimeUnit.SECONDS);

            if (!stopCompleted) {
                stopProcess.destroyForcibly();
                logger.error("Docker compose stop timed out after 60 seconds");
                return false;
            }

            if (stopProcess.exitValue() != 0) {
                logger.warn("Docker compose stop exited with code: " + stopProcess.exitValue());
                // Continue anyway, the containers might not have been running
            } else {
                logger.info("Docker compose stopped successfully");
            }

            // Then, start the containers
            ProcessBuilder startPb = new ProcessBuilder("docker", "compose", "up", "-d");
            startPb.directory(new File(proxyShieldPath));
            startPb.redirectErrorStream(true);

            Process startProcess = startPb.start();
            boolean startCompleted = startProcess.waitFor(120, TimeUnit.SECONDS);

            if (!startCompleted) {
                startProcess.destroyForcibly();
                logger.error("Docker compose start timed out after 120 seconds");
                return false;
            }

            if (startProcess.exitValue() != 0) {
                // Log the output for debugging
                String output = readProcessOutput(startProcess);
                logger.error("Docker compose start failed with exit code: " + startProcess.exitValue() +
                        "\nOutput: " + output);
                return false;
            }

            logger.info("Docker compose restarted successfully");
            return true;

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to restart docker compose: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Update TARGET_HOST and restart docker compose.
     * 
     * @param newTargetHost The new target host value
     * @return true if successful, false otherwise
     */
    public boolean updateAndRestart(String newTargetHost) {
        if (!updateTargetHost(newTargetHost)) {
            return false;
        }
        return restartDockerCompose();
    }

    /**
     * Get the status of the docker compose project.
     * 
     * @return A map containing status information
     */
    public Map<String, String> getStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("proxy_shield_path", proxyShieldPath);
        status.put("env_file_path", envFilePath);
        status.put("target_host", getTargetHost());

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "ps", "--format", "json");
            pb.directory(new File(proxyShieldPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);

            if (completed && process.exitValue() == 0) {
                String output = readProcessOutput(process);
                status.put("docker_status", output);
            } else {
                status.put("docker_status", "error");
            }
        } catch (Exception e) {
            status.put("docker_status", "error: " + e.getMessage());
        }

        return status;
    }

    /**
     * Read the output from a process.
     */
    private String readProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (IOException e) {
            return "Failed to read process output: " + e.getMessage();
        }
    }

    /**
     * Shutdown the controller.
     */
    public void shutdown() {
        logger.info("ProxyShieldController shutting down");
    }
}
