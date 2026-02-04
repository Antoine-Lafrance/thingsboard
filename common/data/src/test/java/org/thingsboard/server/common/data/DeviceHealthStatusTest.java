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
package org.thingsboard.server.common.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.DeviceHealthStatus.ConnectivityStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeviceHealthStatus class
 */
@DisplayName("Device Health Status Tests")
public class DeviceHealthStatusTest {

    private DeviceId testDeviceId;
    private long currentTime;

    @BeforeEach
    public void setUp() {
        testDeviceId = new DeviceId(UUID.randomUUID());
        currentTime = System.currentTimeMillis();
    }

    @Test
    @DisplayName("Should calculate health score correctly for online device with full battery")
    public void testCalculateHealthScore_OnlineFullBattery() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(100)
                .lastActivityTime(currentTime)
                .build();

        // Act
        int healthScore = status.calculateHealthScore();

        // Assert
        assertEquals(100, healthScore, "Health score should be 100 for online device with full battery");
        assertEquals(100, status.getHealthScore(), "Stored health score should match calculated score");
    }

    @Test
    @DisplayName("Should calculate health score correctly for online device with low battery")
    public void testCalculateHealthScore_OnlineLowBattery() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(25)
                .lastActivityTime(currentTime)
                .build();

        // Act
        int healthScore = status.calculateHealthScore();

        // Assert
        assertEquals(70, healthScore, "Health score should be 70 (60 for connectivity + 10 for battery)");
    }

    @Test
    @DisplayName("Should calculate health score correctly for offline device")
    public void testCalculateHealthScore_OfflineDevice() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(50)
                .lastActivityTime(currentTime - 3600000) // 1 hour ago
                .build();

        // Act
        int healthScore = status.calculateHealthScore();

        // Assert
        assertEquals(20, healthScore, "Health score should be 20 (0 for offline + 20 for 50% battery)");
    }

    @Test
    @DisplayName("Should calculate health score correctly for unknown status with no battery info")
    public void testCalculateHealthScore_UnknownStatusNoBattery() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.UNKNOWN)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .build();

        // Act
        int healthScore = status.calculateHealthScore();

        // Assert
        assertEquals(70, healthScore, "Health score should be 70 (30 for unknown + 40 for no battery info)");
    }

    @Test
    @DisplayName("Should identify critical health correctly")
    public void testIsCritical() {
        // Arrange
        DeviceHealthStatus criticalStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(10)
                .lastActivityTime(currentTime)
                .healthScore(4)
                .build();

        DeviceHealthStatus healthyStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime)
                .healthScore(92)
                .build();

        // Act & Assert
        assertTrue(criticalStatus.isCritical(), "Status with score 4 should be critical");
        assertFalse(healthyStatus.isCritical(), "Status with score 92 should not be critical");
    }

    @Test
    @DisplayName("Should identify healthy device correctly")
    public void testIsHealthy() {
        // Arrange
        DeviceHealthStatus healthyStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime)
                .healthScore(92)
                .build();

        DeviceHealthStatus unhealthyStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(20)
                .lastActivityTime(currentTime)
                .healthScore(8)
                .build();

        // Act & Assert
        assertTrue(healthyStatus.isHealthy(), "Status with score 92 should be healthy");
        assertFalse(unhealthyStatus.isHealthy(), "Status with score 8 should not be healthy");
    }

    @Test
    @DisplayName("Should detect inactive device correctly")
    public void testIsInactive() {
        // Arrange
        long tenMinutesAgo = currentTime - (10 * 60 * 1000);
        long twoHoursAgo = currentTime - (2 * 60 * 60 * 1000);
        long fiveMinuteThreshold = 5 * 60 * 1000;

        DeviceHealthStatus recentActivity = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime - (2 * 60 * 1000)) // 2 minutes ago
                .build();

        DeviceHealthStatus oldActivity = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.UNKNOWN)
                .batteryLevel(50)
                .lastActivityTime(twoHoursAgo)
                .build();

        // Act & Assert
        assertFalse(recentActivity.isInactive(fiveMinuteThreshold), 
                "Device active 2 minutes ago should not be inactive with 5 minute threshold");
        assertTrue(oldActivity.isInactive(fiveMinuteThreshold), 
                "Device active 2 hours ago should be inactive with 5 minute threshold");
    }

    @Test
    @DisplayName("Should handle edge case of 0% battery")
    public void testCalculateHealthScore_ZeroBattery() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(0)
                .lastActivityTime(currentTime)
                .build();

        // Act
        int healthScore = status.calculateHealthScore();

        // Assert
        assertEquals(60, healthScore, "Health score should be 60 (60 for online + 0 for empty battery)");
    }

    @Test
    @DisplayName("Should build DeviceHealthStatus with builder pattern")
    public void testBuilderPattern() {
        // Arrange & Act
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(75)
                .lastActivityTime(currentTime)
                .healthScore(90)
                .build();

        // Assert
        assertNotNull(status, "Status should not be null");
        assertEquals(testDeviceId, status.getDeviceId(), "Device ID should match");
        assertEquals(ConnectivityStatus.ONLINE, status.getConnectivityStatus(), "Connectivity status should match");
        assertEquals(75, status.getBatteryLevel(), "Battery level should match");
        assertEquals(currentTime, status.getLastActivityTime(), "Last activity time should match");
        assertEquals(90, status.getHealthScore(), "Health score should match");
    }

    @Test
    @DisplayName("Should handle all connectivity statuses")
    public void testAllConnectivityStatuses() {
        // Test ONLINE
        DeviceHealthStatus onlineStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(100, onlineStatus.calculateHealthScore());

        // Test OFFLINE
        DeviceHealthStatus offlineStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(40, offlineStatus.calculateHealthScore());

        // Test UNKNOWN
        DeviceHealthStatus unknownStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.UNKNOWN)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(70, unknownStatus.calculateHealthScore());
    }

    @Test
    @DisplayName("Should correctly evaluate health threshold boundaries")
    public void testHealthThresholdBoundaries() {
        // Test critical boundary (score = 29)
        DeviceHealthStatus criticalBoundary = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .healthScore(29)
                .build();
        assertTrue(criticalBoundary.isCritical(), "Score 29 should be critical");
        assertFalse(criticalBoundary.isHealthy(), "Score 29 should not be healthy");

        // Test healthy boundary (score = 70)
        DeviceHealthStatus healthyBoundary = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .healthScore(70)
                .build();
        assertFalse(healthyBoundary.isCritical(), "Score 70 should not be critical");
        assertTrue(healthyBoundary.isHealthy(), "Score 70 should be healthy");
    }
}
