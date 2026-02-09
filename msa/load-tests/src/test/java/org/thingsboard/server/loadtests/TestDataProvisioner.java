/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.loadtests;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rest.client.RestClient;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.security.DeviceCredentials;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test Data Provisioner
 * 
 * Creates test data in ThingsBoard instance for load testing:
 * - Tenant users with credentials
 * - Devices with access tokens
 * - Sample telemetry and attributes
 * 
 * This runs before load tests to ensure consistent test environment.
 */
@Slf4j
public class TestDataProvisioner {

    private static final String TB_URL = System.getProperty("tb.url", "http://localhost:8080");
    private static final String TENANT_USERNAME = System.getProperty("tenant.username", "tenant@thingsboard.org");
    private static final String TENANT_PASSWORD = System.getProperty("tenant.password", "tenant");
    private static final int NUM_DEVICES = Integer.getInteger("num.devices", 1000);
    
    public static void main(String[] args) {
        log.info("Starting test data provisioning for ThingsBoard at {}", TB_URL);
        
        try {
            // Wait for ThingsBoard to be ready
            waitForThingsBoard();
            
            // Authenticate as tenant
            RestClient client = new RestClient(TB_URL);
            client.login(TENANT_USERNAME, TENANT_PASSWORD);
            log.info("Authenticated as {}", TENANT_USERNAME);
            
            // Create devices
            List<String> deviceTokens = createDevices(client);
            log.info("Created {} devices", deviceTokens.size());
            
            // Save tokens to file for Gatling feeders
            saveDeviceTokens(deviceTokens);
            
            log.info("Test data provisioning completed successfully");
            
        } catch (Exception e) {
            log.error("Test data provisioning failed", e);
            System.exit(1);
        }
    }
    
    /**
     * Wait for ThingsBoard to be ready to accept connections
     */
    private static void waitForThingsBoard() throws InterruptedException {
        log.info("Waiting for ThingsBoard to be ready...");
        int maxAttempts = 60;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            try {
                RestClient testClient = new RestClient(TB_URL);
                testClient.login(TENANT_USERNAME, TENANT_PASSWORD);
                log.info("ThingsBoard is ready!");
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new RuntimeException("ThingsBoard did not become ready in time", e);
                }
                log.debug("Attempt {}/{}: ThingsBoard not ready yet, waiting...", attempt, maxAttempts);
                TimeUnit.SECONDS.sleep(5);
            }
        }
    }
    
    /**
     * Create test devices with predictable tokens
     */
    private static List<String> createDevices(RestClient client) {
        List<String> tokens = new ArrayList<>();
        
        for (int i = 0; i < NUM_DEVICES; i++) {
            try {
                // Create device
                Device device = new Device();
                device.setName("LoadTest_Device_" + i);
                device.setType("LoadTest");
                device.setLabel("Load Test Device " + i);
                
                Device savedDevice = client.saveDevice(device);
                
                // Get device credentials
                DeviceCredentials credentials = client.getDeviceCredentialsByDeviceId(savedDevice.getId());
                tokens.add(credentials.getCredentialsId());
                
                if ((i + 1) % 100 == 0) {
                    log.info("Created {} devices so far...", i + 1);
                }
                
            } catch (Exception e) {
                log.warn("Failed to create device {}: {}", i, e.getMessage());
            }
        }
        
        return tokens;
    }
    
    /**
     * Save device tokens to file for Gatling feeder
     */
    private static void saveDeviceTokens(List<String> tokens) {
        try {
            String tokensFile = "target/device-tokens.txt";
            java.nio.file.Files.write(
                java.nio.file.Paths.get(tokensFile),
                tokens,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
            log.info("Saved {} device tokens to {}", tokens.size(), tokensFile);
        } catch (Exception e) {
            log.error("Failed to save device tokens", e);
        }
    }
}
