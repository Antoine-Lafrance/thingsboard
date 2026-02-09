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

/**
 * REST API Authentication and Device Query Load Test
 * 
 * Simulates tenant users authenticating and querying device lists.
 * This test measures the performance of authentication flow and paginated device queries.
 * 
 * Load Profile:
 * - Constant rate: 20 requests/second for 5 minutes
 * - 50-100 concurrent users
 * 
 * Endpoints Tested:
 * - POST /api/auth/login (JWT authentication)
 * - GET /api/tenant/devices (paginated device listings)
 * 
 * Success Criteria:
 * - Authentication P95 < 1000ms
 * - Device queries P95 < 2000ms
 * - Error rate < 0.5%
 */
class RestApiSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")
  val targetRps = Integer.getInteger("targetRps", 20).intValue()
  val testDuration = Integer.getInteger("duration", 300).intValue()
  val concurrentUsers = Integer.getInteger("users", 50).intValue()
  
  // Test credentials - in production, these come from TestDataProvisioner
  val testUsername = System.getProperty("testUser", "tenant@thingsboard.org")
  val testPassword = System.getProperty("testPassword", "tenant")
  
  // HTTP protocol
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-RestApiTest/1.0")
    .shareConnections

  // Scenario 1: Authenticate and obtain JWT token
  val authScenario = scenario("User Authentication")
    .exec(
      http("Login")
        .post("/api/auth/login")
        .body(StringBody(s"""{"username":"$testUsername","password":"$testPassword"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
        .check(jsonPath("$.refreshToken").saveAs("refreshToken"))
        .check(responseTimeInMillis.lte(3000))
    )
    .pause(1.second)
    .exec(
      http("Get Current User")
        .get("/api/auth/user")
        .header("X-Authorization", "Bearer ${jwtToken}")
        .check(status.is(200))
        .check(jsonPath("$.email").is(testUsername))
    )

  // Scenario 2: Query devices with authentication
  val deviceQueryScenario = scenario("Device Queries")
    .exec(
      http("Login")
        .post("/api/auth/login")
        .body(StringBody(s"""{"username":"$testUsername","password":"$testPassword"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )
    .pause(500.milliseconds)
    .repeat(10) {
      exec(
        http("Query Devices - Page ${__gatling.loop.counter}")
          .get("/api/tenant/devices")
          .queryParam("pageSize", "20")
          .queryParam("page", "${__gatling.loop.counter}")
          .header("X-Authorization", "Bearer ${jwtToken}")
          .check(status.is(200))
          .check(jsonPath("$.data").exists)
          .check(responseTimeInMillis.lte(5000))
      )
      .pause(500.milliseconds, 2.seconds)
    }

  // Scenario 3: Mixed workload - authentication + queries
  val mixedScenario = scenario("Mixed API Workload")
    .exec(
      http("Login")
        .post("/api/auth/login")
        .body(StringBody(s"""{"username":"$testUsername","password":"$testPassword"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )
    .pause(1.second)
    .during(testDuration.seconds) {
      randomSwitch(
        60.0 -> exec(  // 60% device queries
          http("Query Devices")
            .get("/api/tenant/devices")
            .queryParam("pageSize", "50")
            .queryParam("page", "0")
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.is(200))
        ),
        20.0 -> exec(  // 20% device details
          http("Get Device by ID")
            .get("/api/device/info/784f394c-42b6-435a-983c-b7beff2784f9")  // Sample device ID
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.in(200, 404))  // Accept not found in test environment
        ),
        20.0 -> exec(  // 20% user info refresh
          http("Get User Info")
            .get("/api/auth/user")
            .header("X-Authorization", "Bearer ${jwtToken}")
            .check(status.is(200))
        )
      )
      .pause(2.seconds, 5.seconds)
    }

  // Load injection
  setUp(
    authScenario.inject(
      constantConcurrentUsers(10).during(testDuration.seconds)
    ),
    deviceQueryScenario.inject(
      rampConcurrentUsers(0).to(20).during(30.seconds),
      constantConcurrentUsers(20).during((testDuration - 30).seconds)
    ),
    mixedScenario.inject(
      rampConcurrentUsers(0).to(concurrentUsers).during(60.seconds),
      constantConcurrentUsers(concurrentUsers).during((testDuration - 60).seconds)
    )
  ).protocols(httpProtocol)
  .assertions(
    global.responseTime.percentile3.lt(2000),   // P95 < 2s for all requests
    global.successfulRequests.percent.gt(99.5), // Error rate < 0.5%
    forAll.failedRequests.percent.lte(1.0)      // No scenario exceeds 1% errors
  )
}
