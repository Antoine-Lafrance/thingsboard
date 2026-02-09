# ThingsBoard Load Tests

This module contains Gatling-based load and performance tests for ThingsBoard platform.

## Overview

The load tests simulate realistic workloads to measure ThingsBoard's performance characteristics including throughput, response times, and resource utilization under stress. Tests run in isolated Docker environments for reproducible results.

## Test Scenarios

### 1. Telemetry Ingestion Simulation

- **File:** `TelemetryIngestionSimulation.scala`
- **Description:** Simulates 500-1000 virtual devices posting telemetry data via HTTP transport
- **Endpoint:** `POST /api/v1/{deviceToken}/telemetry`
- **Load Profile:** Ramp up over 2 minutes, sustain for 3 minutes, ramp down over 1 minute
- **Success Criteria:**
  - 95th percentile response time < 500ms
  - Error rate < 1%
  - Throughput > 1000 requests/second

### 2. REST API Authentication Simulation

- **File:** `RestApiSimulation.scala`
- **Description:** Simulates tenant users authenticating and querying device lists
- **Endpoints:**
  - `POST /api/auth/login` (JWT authentication)
  - `GET /api/tenant/devices` (paginated queries)
- **Load Profile:** Constant 20 req/sec for 5 minutes
- **Success Criteria:**
  - Authentication: 95th percentile < 1000ms
  - Device queries: 95th percentile < 2000ms
  - Error rate < 0.5%

### 3. Dashboard Workload Simulation

- **File:** `DashboardWorkloadSimulation.scala`
- **Description:** Simulates dashboard refresh patterns with mixed read/write workload
- **Endpoints:**
  - `GET /{entityType}/{entityId}/values/timeseries` (75% of requests)
  - `POST /api/v1/{deviceToken}/telemetry` (25% of requests)
- **Load Profile:** 30 concurrent users, each monitoring 10 devices, with 2-5 second think time
- **Success Criteria:**
  - Read queries: 95th percentile < 800ms
  - Write requests: 95th percentile < 500ms
  - Error rate < 1%

## Running Load Tests

### Prerequisites

- Docker and Docker Compose installed
- Java 17+
- Maven 3.6+

### Local Execution

1. **Build the project:**

   ```bash
   cd thingsboard
   mvn clean install -DskipTests
   ```

2. **Start the test environment:**

   ```bash
   cd msa/load-tests
   docker compose -f docker-compose.loadtest.yml up -d
   ```

3. **Wait for ThingsBoard to be ready** (about 60-90 seconds):

   ```bash
   docker compose -f docker-compose.loadtest.yml logs -f tb-core
   # Wait for "ThingsBoard started successfully"
   ```

4. **Run all load tests:**

   ```bash
   mvn gatling:test -DloadTests.skip=false
   ```

5. **Run a specific simulation:**

   ```bash
   mvn gatling:test -Dgatling.simulationClass=org.thingsboard.server.loadtests.TelemetryIngestionSimulation
   ```

6. **View reports:**

   ```bash
   open target/gatling/*/index.html  # macOS/Linux
   start target/gatling/*/index.html  # Windows
   ```

7. **Cleanup:**
   ```bash
   docker compose -f docker-compose.loadtest.yml down -v
   ```

## CI/CD Integration

Load tests are integrated into the GitHub Actions CI/CD pipeline:

- **Trigger:** Runs only on `master`/`main` branch pushes
- **Environment:** Isolated docker-compose stack
- **Job:** `load-tests` (runs after `code-quality`)
- **Artifacts:** Gatling HTML reports (30 days retention)
- **Failure Policy:** Report-only mode (doesn't block releases)

## Performance Baselines

Expected performance on CI infrastructure (GitHub Actions ubuntu-latest):

| Metric          | Telemetry Ingestion | REST API  | Dashboard Workload |
| --------------- | ------------------- | --------- | ------------------ |
| **Throughput**  | ~1200 req/s         | ~25 req/s | ~150 req/s         |
| **P50 Latency** | ~80ms               | ~200ms    | ~120ms             |
| **P95 Latency** | ~400ms              | ~900ms    | ~600ms             |
| **P99 Latency** | ~480ms              | ~1800ms   | ~750ms             |
| **Error Rate**  | <0.5%               | <0.2%     | <0.5%              |

> **Note:** Actual performance varies based on infrastructure. Baselines should be updated after 3+ stable runs.

## Configuration

### Gatling Configuration

Edit [src/test/resources/gatling.conf](src/test/resources/gatling.conf) to customize:

- Simulation timeouts
- Report generation settings
- HTTP client configuration
- Data writer settings

### Load Profiles

Each simulation class contains configurable parameters:

```scala
val numDevices = Integer.getInteger("devices", 1000).intValue()
val rampDuration = Integer.getInteger("rampDuration", 120).intValue()
val sustainDuration = Integer.getInteger("sustainDuration", 180).intValue()
```

Override via system properties:

```bash
mvn gatling:test -Ddevices=2000 -DrampDuration=180
```

## Troubleshooting

### Out of Memory Errors

Increase JVM heap size in [pom.xml](pom.xml):

```xml
<jvmArgs>
  <jvmArg>-Xmx4g</jvmArg>
</jvmArgs>
```

### Connection Timeouts

Check ThingsBoard container health:

```bash
docker compose -f docker-compose.loadtest.yml ps
curl http://localhost:8080/api/noauth/health
```

### Test Data Issues

Re-provision test data:

```bash
mvn test -Dtest=TestDataProvisioner
```

## Contributing

When adding new load test scenarios:

1. Follow existing naming pattern: `*Simulation.scala`
2. Include inline documentation with scenario description
3. Define clear success criteria assertions
4. Update this README with scenario details
5. Update performance baselines after validation

## License

Copyright © 2016-2026 The Thingsboard Authors

Licensed under Apache License 2.0
