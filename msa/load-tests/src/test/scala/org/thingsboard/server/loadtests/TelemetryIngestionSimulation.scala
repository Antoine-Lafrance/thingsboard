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
package org.thingsboard.server.loadtests

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Telemetry Ingestion Load Test
 * 
 * Simulates 500-1000 virtual devices posting telemetry data via HTTP transport.
 * This test measures the throughput and latency of the core telemetry ingestion pipeline.
 * 
 * Load Profile:
 * - Ramp up: 500 devices over 2 minutes
 * - Sustain: Continue for 3 minutes at peak load
 * - Ramp down: 1 minute
 * 
 * Endpoints Tested:
 * - POST /api/v1/{deviceToken}/telemetry
 * 
 * Success Criteria:
 * - 95th percentile response time < 500ms
 * - Error rate < 1%
 * - Sustained throughput > 1000 req/s
 */
class TelemetryIngestionSimulation extends Simulation {

  // Configuration from system properties
  val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")
  val numDevices = Integer.getInteger("devices", 1000).intValue()
  val rampDuration = Integer.getInteger("rampDuration", 120).intValue()
  val sustainDuration = Integer.getInteger("sustainDuration", 180).intValue()
  val rampDownDuration = Integer.getInteger("rampDownDuration", 60).intValue()
  
  // HTTP protocol configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-TelemetryTest/1.0")
    .shareConnections

  // Device token feeder - simulates pre-provisioned devices
  // In real scenario, these would be actual device tokens from test data provisioning
  val deviceTokenFeeder = Iterator.continually(Map(
    "deviceToken" -> s"device_token_${Random.alphanumeric.take(20).mkString}"
  ))

  // Telemetry payload generator - simulates realistic IoT sensor data
  def generateTelemetryPayload(): String = {
    val temperature = 20 + Random.nextDouble() * 15  // 20-35°C
    val humidity = 30 + Random.nextDouble() * 50     // 30-80%
    val batteryLevel = 50 + Random.nextInt(50)       // 50-100%
    val timestamp = System.currentTimeMillis()
    
    s"""{
      "ts": $timestamp,
      "values": {
        "temperature": $temperature,
        "humidity": $humidity,
        "batteryLevel": $batteryLevel,
        "status": "active"
      }
    }"""
  }

  // Scenario: Device sends telemetry periodically
  val deviceScenario = scenario("Device Telemetry Ingestion")
    .feed(deviceTokenFeeder)
    .during(sustainDuration.seconds) {
      exec(
        http("Post Telemetry")
          .post("/api/v1/${deviceToken}/telemetry")
          .body(StringBody(session => generateTelemetryPayload()))
          .check(status.in(200, 202))  // Accept both sync (200) and async (202) responses
          .check(responseTimeInMillis.lte(2000))  // Individual request max 2s
      )
      .pause(3.seconds, 7.seconds)  // Devices send data every 3-7 seconds (typical IoT pattern)
    }

  // Load injection profile
  setUp(
    deviceScenario.inject(
      rampUsers(numDevices).during(rampDuration.seconds),  // Gradual ramp up
      nothingFor(sustainDuration.seconds),                  // Sustain peak load
      rampUsers(0).during(rampDownDuration.seconds)        // Graceful ramp down
    ).protocols(httpProtocol)
  ).assertions(
    global.responseTime.percentile3.lt(500),   // P95 < 500ms
    global.successfulRequests.percent.gt(99),  // Error rate < 1%
    global.requestsPerSec.gte(1000)            // Throughput >= 1000 req/s at peak
  )
}
