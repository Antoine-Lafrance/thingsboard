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

    @Test
    @DisplayName("Should handle boundary battery levels correctly")
    public void testBoundaryBatteryLevels() {
        // Test 1% battery
        DeviceHealthStatus onePercent = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(1)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(60, onePercent.calculateHealthScore(), 
                "Health score should be 60 (60 for online + 0 for 1% battery)");

        // Test 99% battery
        DeviceHealthStatus ninetyNinePercent = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(99)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(99, ninetyNinePercent.calculateHealthScore(), 
                "Health score should be 99 (60 for online + 39 for 99% battery)");
    }

    @Test
    @DisplayName("Should handle multiple score calculations on same object")
    public void testMultipleCalculations() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime)
                .build();

        // Act - Calculate multiple times
        int firstScore = status.calculateHealthScore();
        int secondScore = status.calculateHealthScore();
        int thirdScore = status.calculateHealthScore();

        // Assert
        assertEquals(92, firstScore, "First calculation should return 92");
        assertEquals(92, secondScore, "Second calculation should return 92");
        assertEquals(92, thirdScore, "Third calculation should return 92");
        assertEquals(92, status.getHealthScore(), "Stored score should be 92");
    }

    @Test
    @DisplayName("Should detect inactivity at exact threshold")
    public void testInactivityAtExactThreshold() {
        // Arrange
        long threshold = 5 * 60 * 1000; // 5 minutes
        
        // Use a time far enough in the past to avoid timing issues
        // Activity was 4 minutes ago - should NOT be inactive with 5 minute threshold
        DeviceHealthStatus recentStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime - (4 * 60 * 1000))
                .build();

        // Activity was 6 minutes ago - should be inactive with 5 minute threshold
        DeviceHealthStatus oldStatus = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime - (6 * 60 * 1000))
                .build();

        // Act & Assert
        assertFalse(recentStatus.isInactive(threshold), 
                "Device with 4 min old activity should not be inactive with 5 min threshold");
        
        assertTrue(oldStatus.isInactive(threshold), 
                "Device with 6 min old activity should be inactive with 5 min threshold");
    }

    @Test
    @DisplayName("Should handle very long inactivity periods")
    public void testVeryLongInactivity() {
        // Arrange - 30 days ago
        long thirtyDaysAgo = currentTime - (30L * 24 * 60 * 60 * 1000);
        
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(0)
                .lastActivityTime(thirtyDaysAgo)
                .build();

        // Act & Assert
        assertTrue(status.isInactive(1 * 60 * 1000), 
                "Device should be inactive after 30 days with 1 minute threshold");
        assertTrue(status.isInactive(24 * 60 * 60 * 1000), 
                "Device should be inactive after 30 days with 24 hour threshold");
    }

    @Test
    @DisplayName("Should handle mid-range battery levels accurately")
    public void testMidRangeBatteryLevels() {
        // Test 50% battery
        DeviceHealthStatus fiftyPercent = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(50)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(80, fiftyPercent.calculateHealthScore(), 
                "Health score should be 80 (60 + 20)");

        // Test 75% battery
        DeviceHealthStatus seventyFivePercent = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(75)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(90, seventyFivePercent.calculateHealthScore(), 
                "Health score should be 90 (60 + 30)");

        // Test 25% battery with UNKNOWN status
        DeviceHealthStatus unknownWithLowBattery = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.UNKNOWN)
                .batteryLevel(25)
                .lastActivityTime(currentTime)
                .build();
        assertEquals(40, unknownWithLowBattery.calculateHealthScore(), 
                "Health score should be 40 (30 + 10)");
    }

    @Test
    @DisplayName("Should evaluate critical and healthy status at score 30")
    public void testScoreThirtyBoundary() {
        // Test score = 30 (should NOT be critical, should NOT be healthy)
        DeviceHealthStatus scoreThirty = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.UNKNOWN)
                .batteryLevel(null)
                .lastActivityTime(currentTime)
                .healthScore(30)
                .build();

        assertFalse(scoreThirty.isCritical(), 
                "Score 30 should not be critical (threshold is < 30)");
        assertFalse(scoreThirty.isHealthy(), 
                "Score 30 should not be healthy (threshold is >= 70)");
    }

    @Test
    @DisplayName("Should evaluate status for score 69")
    public void testScoreSixtyNineBoundary() {
        // Test score = 69 (should NOT be critical, should NOT be healthy)
        DeviceHealthStatus scoreSixtyNine = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(22) // 60 + 8 = 68, but let's use 23 for 69
                .lastActivityTime(currentTime)
                .build();
        
        scoreSixtyNine.calculateHealthScore();
        
        assertFalse(scoreSixtyNine.isCritical(), 
                "Score 69 should not be critical");
        assertFalse(scoreSixtyNine.isHealthy(), 
                "Score 69 should not be healthy (need >= 70)");
    }

    @Test
    @DisplayName("Should handle offline device with full battery")
    public void testOfflineWithFullBattery() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.OFFLINE)
                .batteryLevel(100)
                .lastActivityTime(currentTime - 3600000) // 1 hour ago
                .build();

        // Act
        int healthScore = status.calculateHealthScore();

        // Assert
        assertEquals(40, healthScore, 
                "Offline device even with full battery should score 40 (0 + 40)");
        assertFalse(status.isCritical(), 
                "Score 40 is not critical (critical is < 30), but still not healthy");
        assertFalse(status.isHealthy(), 
                "Score 40 is not healthy (healthy is >= 70)");
    }

    @Test
    @DisplayName("Should handle zero threshold for inactivity check")
    public void testInactivityWithZeroThreshold() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime - 1) // 1ms ago
                .build();

        // Act & Assert
        assertTrue(status.isInactive(0), 
                "Any past activity should be inactive with zero threshold");
    }

    @Test
    @DisplayName("Should handle recent activity with large threshold")
    public void testRecentActivityWithLargeThreshold() {
        // Arrange - 1 second ago, threshold 1 year
        long oneYearInMs = 365L * 24 * 60 * 60 * 1000;
        
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(90)
                .lastActivityTime(currentTime - 1000) // 1 second ago
                .build();

        // Act & Assert
        assertFalse(status.isInactive(oneYearInMs), 
                "Recent activity should not be inactive with 1 year threshold");
    }

    @Test
    @DisplayName("Should maintain data consistency with Lombok annotations")
    public void testDataConsistency() {
        // Arrange
        DeviceHealthStatus status1 = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(85)
                .lastActivityTime(currentTime)
                .healthScore(94)
                .build();

        DeviceHealthStatus status2 = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(85)
                .lastActivityTime(currentTime)
                .healthScore(94)
                .build();

        // Act & Assert - Test equals and hashCode
        assertEquals(status1, status2, "Objects with same values should be equal");
        assertEquals(status1.hashCode(), status2.hashCode(), 
                "Objects with same values should have same hashCode");
        assertEquals(status1.toString(), status2.toString(), 
                "Objects with same values should have same toString output");
    }

    @Test
    @DisplayName("Should allow modification after creation")
    public void testMutability() {
        // Arrange
        DeviceHealthStatus status = DeviceHealthStatus.builder()
                .deviceId(testDeviceId)
                .connectivityStatus(ConnectivityStatus.ONLINE)
                .batteryLevel(80)
                .lastActivityTime(currentTime)
                .build();

        // Act - Modify values
        status.setConnectivityStatus(ConnectivityStatus.OFFLINE);
        status.setBatteryLevel(20);
        
        int newScore = status.calculateHealthScore();

        // Assert
        assertEquals(ConnectivityStatus.OFFLINE, status.getConnectivityStatus());
        assertEquals(20, status.getBatteryLevel());
        assertEquals(8, newScore, "New score should reflect updated values (0 + 8)");
    }
}
