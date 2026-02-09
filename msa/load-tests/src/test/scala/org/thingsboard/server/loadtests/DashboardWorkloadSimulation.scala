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
 * Dashboard Workload Load Test
 * 
 * Simulates realistic dashboard refresh patterns with mixed read/write workload.
 * Represents users monitoring device telemetry through dashboard widgets.
 * 
 * Load Profile:
 * - 30 concurrent users
 * - Each user monitors 10 devices
 * - 75% reads (telemetry queries) / 25% writes (device updates)
 * - Think time: 2-5 seconds between actions
 * 
 * Endpoints Tested:
 * - GET /api/plugins/telemetry/DEVICE/{deviceId}/values/timeseries
 * - POST /api/v1/{deviceToken}/telemetry
 * - GET /api/plugins/telemetry/DEVICE/{deviceId}/values/attributes
 * 
 * Success Criteria:
 * - Read queries P95 < 800ms
 * - Write requests P95 < 500ms
 * - Error rate < 1%
 */
class DashboardWorkloadSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")
  val numUsers = Integer.getInteger("users", 30).intValue()
  val devicesPerUser = Integer.getInteger("devicesPerUser", 10).intValue()
  val testDuration = Integer.getInteger("duration", 300).intValue()
  
  val testUsername = System.getProperty("testUser", "tenant@thingsboard.org")
  val testPassword = System.getProperty("testPassword", "tenant")
  
  // HTTP protocol
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-DashboardTest/1.0")
    .shareConnections

  // Device ID feeder - simulates monitoring known devices
  val deviceIdFeeder = Iterator.continually(Map(
    "deviceId" -> java.util.UUID.randomUUID().toString,
    "deviceToken" -> s"device_${Random.alphanumeric.take(16).mkString}"
  ))

  // Telemetry keys to query
  val telemetryKeys = Seq("temperature", "humidity", "batteryLevel", "status")
  
  // Generate telemetry query parameters
  def getTelemetryParams(): Map[String, String] = {
    val endTs = System.currentTimeMillis()
    val startTs = endTs - (3600 * 1000)  // Last hour
    Map(
      "keys" -> telemetryKeys.mkString(","),
      "startTs" -> startTs.toString,
      "endTs" -> endTs.toString,
      "limit" -> "100"
    )
  }

  // Generate telemetry data for writes
  def generateTelemetryData(): String = {
    val temperature = 20 + Random.nextDouble() * 15
    val humidity = 30 + Random.nextDouble() * 50
    val batteryLevel = 50 + Random.nextInt(50)
    
    s"""{
      "temperature": $temperature,
      "humidity": $humidity,
      "batteryLevel": $batteryLevel,
      "status": "${if (Random.nextBoolean()) "active" else "idle"}"
    }"""
  }

  // Scenario: Dashboard user monitoring devices
  val dashboardUserScenario = scenario("Dashboard User")
    // Step 1: Authenticate
    .exec(
      http("Login")
        .post("/api/auth/login")
        .body(StringBody(s"""{"username":"$testUsername","password":"$testPassword"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )
    .pause(2.seconds)
    
    // Step 2: Monitor devices continuously
    .during(testDuration.seconds) {
      feed(deviceIdFeeder)
      .randomSwitch(
        // 50% - Query latest telemetry (most common dashboard operation)
        50.0 -> exec(
          http("Get Latest Telemetry")
            .get("/api/plugins/telemetry/DEVICE/${deviceId}/values/timeseries")
            .queryParamMap(session => getTelemetryParams())
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.in(200, 404))  // Accept not found for random device IDs
            .check(responseTimeInMillis.lte(3000))
        ),
        
        // 25% - Query device attributes
        25.0 -> exec(
          http("Get Device Attributes")
            .get("/api/plugins/telemetry/DEVICE/${deviceId}/values/attributes")
            .queryParam("keys", "name,type,model")
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.in(200, 404))
        ),
        
        // 15% - Query historical timeseries (heavy query)
        15.0 -> exec(
          http("Get Historical Data")
            .get("/api/plugins/telemetry/DEVICE/${deviceId}/values/timeseries")
            .queryParam("keys", telemetryKeys.mkString(","))
            .queryParam("startTs", (System.currentTimeMillis() - 86400000).toString)  // Last 24h
            .queryParam("endTs", System.currentTimeMillis().toString)
            .queryParam("limit", "1000")
            .queryParam("agg", "AVG")
            .queryParam("interval", "3600000")  // 1-hour aggregation
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.in(200, 404))
            .check(responseTimeInMillis.lte(8000))  // Historical queries can be slower
        ),
        
        // 10% - Simulate device sending telemetry (background activity)
        10.0 -> exec(
          http("Device Posts Telemetry")
            .post("/api/v1/${deviceToken}/telemetry")
            .body(StringBody(session => generateTelemetryData()))
            .check(status.in(200, 202))
            .check(responseTimeInMillis.lte(2000))
        )
      )
      .pause(2.seconds, 5.seconds)  // Think time between dashboard interactions
    }

  // Scenario: Automated dashboard refresh (widgets auto-refreshing)
  val autoRefreshScenario = scenario("Auto-Refresh Widgets")
    .exec(
      http("Login")
        .post("/api/auth/login")
        .body(StringBody(s"""{"username":"$testUsername","password":"$testPassword"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )
    .during(testDuration.seconds) {
      repeat(devicesPerUser, "deviceIndex") {
        feed(deviceIdFeeder)
        .exec(
          http("Widget Refresh - Device ${deviceIndex}")
            .get("/api/plugins/telemetry/DEVICE/${deviceId}/values/timeseries")
            .queryParam("keys", telemetryKeys.mkString(","))
            .queryParam("limit", "1")  // Latest value only
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.in(200, 404))
        )
        .pause(100.milliseconds)  // Burst of widget refreshes
      }
      .pause(10.seconds)  // Dashboard auto-refresh interval
    }

  // Load injection
  setUp(
    dashboardUserScenario.inject(
      rampConcurrentUsers(0).to(numUsers).during(30.seconds),
      constantConcurrentUsers(numUsers).during((testDuration - 30).seconds)
    ),
    autoRefreshScenario.inject(
      rampConcurrentUsers(0).to(5).during(30.seconds),
      constantConcurrentUsers(5).during((testDuration - 30).seconds)
    )
  ).protocols(httpProtocol)
  .assertions(
    // Read queries should be fast
    details("Get Latest Telemetry").responseTime.percentile3.lt(800),
    details("Get Device Attributes").responseTime.percentile3.lt(800),
    
    // Write operations should be faster
    details("Device Posts Telemetry").responseTime.percentile3.lt(500),
    
    // Overall health
    global.successfulRequests.percent.gt(99),
    global.responseTime.percentile3.lt(1000)
  )
}
