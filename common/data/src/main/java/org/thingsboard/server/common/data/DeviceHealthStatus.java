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

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;
import org.thingsboard.server.common.data.id.DeviceId;

/**
 * Device Health Status - Simple feature to track device health metrics
 * This class represents the health status of a device including connectivity,
 * battery level, and last activity timestamp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Device Health Status containing connectivity state, battery level, and activity information")
public class DeviceHealthStatus {

    @Schema(description = "Device ID", required = true)
    private DeviceId deviceId;

    @Schema(description = "Device connectivity status (ONLINE, OFFLINE, UNKNOWN)", required = true)
    private ConnectivityStatus connectivityStatus;

    @Schema(description = "Device battery level percentage (0-100), null if not applicable")
    private Integer batteryLevel;

    @Schema(description = "Last activity timestamp in milliseconds", required = true)
    private long lastActivityTime;

    @Schema(description = "Health score calculated from connectivity and battery (0-100)", required = true)
    private int healthScore;

    public enum ConnectivityStatus {
        ONLINE,
        OFFLINE,
        UNKNOWN
    }

    /**
     * Calculate the health score based on connectivity status and battery level
     * @return health score from 0 to 100
     */
    public int calculateHealthScore() {
        int score = 0;

        // Connectivity contribution (60% of score)
        switch (connectivityStatus) {
            case ONLINE:
                score += 60;
                break;
            case OFFLINE:
                score += 0;
                break;
            case UNKNOWN:
                score += 30;
                break;
        }

        // Battery level contribution (40% of score)
        if (batteryLevel != null) {
            score += (batteryLevel * 40) / 100;
        } else {
            // If no battery info, give full battery score
            score += 40;
        }

        this.healthScore = score;
        return score;
    }

    /**
     * Check if device health is critical (health score below 30)
     * @return true if health is critical
     */
    public boolean isCritical() {
        return healthScore < 30;
    }

    /**
     * Check if device health is good (health score above 70)
     * @return true if health is good
     */
    public boolean isHealthy() {
        return healthScore >= 70;
    }

    /**
     * Check if the device is inactive for more than specified milliseconds
     * @param thresholdMs inactivity threshold in milliseconds
     * @return true if device is inactive
     */
    public boolean isInactive(long thresholdMs) {
        return (System.currentTimeMillis() - lastActivityTime) > thresholdMs;
    }
}
