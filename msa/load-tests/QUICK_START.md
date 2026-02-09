# Load Tests Quick Start Guide

## What Was Implemented

✅ **3 Gatling Load Test Scenarios:**
1. **Telemetry Ingestion** - 500-1000 virtual devices posting sensor data
2. **REST API Authentication** - Concurrent user login and device queries  
3. **Dashboard Workload** - Mixed read/write operations simulating dashboard use

✅ **CI/CD Integration:**
- New `load-tests` job runs after code quality checks
- Only executes on `master`/`main` branch (saves CI resources)
- Report-only mode (doesn't block releases)
- Gatling HTML reports uploaded as artifacts (30-day retention)

## Running Locally

### Quick Test (Linux/macOS/WSL)

```bash
# 1. Build the project
cd thingsboard
mvn clean install -DskipTests

# 2. Navigate to load tests
cd msa/load-tests

# 3. Start ThingsBoard
docker compose -f docker-compose.loadtest.yml up -d

# 4. Wait for health check (~2 minutes)
until curl -f http://localhost:8080/api/noauth/health 2>/dev/null; do
  echo "Waiting for ThingsBoard..."
  sleep 10
done

# 5. Run load tests
mvn gatling:test -DloadTests.skip=false

# 6. View reports (opens in browser)
open target/gatling/*/index.html

# 7. Cleanup
docker compose -f docker-compose.loadtest.yml down -v
```

### Windows PowerShell

```powershell
# 1-2. Build and navigate
cd thingsboard
mvn clean install -DskipTests
cd msa\load-tests

# 3. Start environment
docker compose -f docker-compose.loadtest.yml up -d

# 4. Wait for health check
$timeout = 180
$elapsed = 0
while ($elapsed -lt $timeout) {
    try {
        Invoke-WebRequest -Uri http://localhost:8080/api/noauth/health -UseBasicParsing | Out-Null
        Write-Host "✅ ThingsBoard ready!"
        break
    } catch {
        Write-Host "Waiting... ($elapsed/$timeout seconds)"
        Start-Sleep -Seconds 10
        $elapsed += 10
    }
}

# 5. Run tests
mvn gatling:test -DloadTests.skip=false

# 6. Open report
start target\gatling\*\index.html

# 7. Cleanup
docker compose -f docker-compose.loadtest.yml down -v
```

## Running Individual Scenarios

Run a specific simulation:

```bash
# Telemetry ingestion only
mvn gatling:test -Dgatling.simulationClass=org.thingsboard.server.loadtests.TelemetryIngestionSimulation

# REST API only
mvn gatling:test -Dgatling.simulationClass=org.thingsboard.server.loadtests.RestApiSimulation

# Dashboard workload only
mvn gatling:test -Dgatling.simulationClass=org.thingsboard.server.loadtests.DashboardWorkloadSimulation
```

## Customizing Load Parameters

Override defaults via system properties:

```bash
# More devices and longer test
mvn gatling:test \
  -Dgatling.simulationClass=org.thingsboard.server.loadtests.TelemetryIngestionSimulation \
  -Ddevices=2000 \
  -DrampDuration=300 \
  -DsustainDuration=600
```

Available parameters:
- `devices` - Number of simulated devices (default: 1000)
- `rampDuration` - Ramp-up time in seconds (default: 120)
- `sustainDuration` - Peak load duration in seconds (default: 180)
- `users` - Concurrent users for API tests (default: 50)
- `baseUrl` - ThingsBoard URL (default: http://localhost:8080)

## CI/CD Pipeline

The load tests run automatically in GitHub Actions:

**Trigger:** Push to `master` or `main` branch

**Pipeline Flow:**
```
build → unit-tests ─┐
                    ├─→ code-quality → load-tests → release → notification
device-health-tests ┘
```

**Viewing Results:**
1. Go to GitHub Actions tab
2. Click on workflow run
3. Scroll to "Artifacts" section
4. Download `gatling-load-test-reports`
5. Extract and open `index.html`

## Troubleshooting

### Docker Container Issues
```bash
# Check container status
docker compose -f docker-compose.loadtest.yml ps

# View logs
docker compose -f docker-compose.loadtest.yml logs -f tb-core

# Restart clean
docker compose -f docker-compose.loadtest.yml down -v
docker compose -f docker-compose.loadtest.yml up -d
```

### Java Out of Memory
Edit [pom.xml](pom.xml) and increase heap:
```xml
<jvmArgs>
  <jvmArg>-Xmx4g</jvmArg>
</jvmArgs>
```

### Port Conflicts
Stop other services using ports 8080, 5432, 6379, or modify [docker-compose.loadtest.yml](docker-compose.loadtest.yml)

## Performance Expectations

On GitHub Actions runners (4 CPU cores, 16GB RAM):

| Scenario | Expected Throughput | P95 Latency |
|----------|-------------------|-------------|
| Telemetry Ingestion | ~1200 req/s | < 500ms |
| REST API | ~25 req/s | < 2000ms |
| Dashboard Workload | ~150 req/s | < 800ms |

Local performance may vary based on hardware.

## Next Steps

- Run baseline tests 3 times on your main branch
- Document actual performance baselines
- Adjust assertions in simulation files if needed
- Consider adding more scenarios for specific features
- Set up performance regression tracking

## Files Created

```
msa/load-tests/
├── pom.xml                                          # Maven configuration
├── README.md                                        # Detailed documentation
├── QUICK_START.md                                   # This file
├── docker-compose.loadtest.yml                      # Test environment
├── src/test/
│   ├── java/org/thingsboard/server/loadtests/
│   │   └── TestDataProvisioner.java                # Test data setup
│   ├── scala/org/thingsboard/server/loadtests/
│   │   ├── TelemetryIngestionSimulation.scala      # Load test 1
│   │   ├── RestApiSimulation.scala                 # Load test 2
│   │   └── DashboardWorkloadSimulation.scala       # Load test 3
│   └── resources/
│       ├── gatling.conf                            # Gatling config
│       ├── logback-test.xml                        # Logging config
│       └── gatling-results-to-markdown.sh          # Report converter
```

Updated files:
- [msa/pom.xml](msa/pom.xml) - Added load-tests module profile
- [.github/workflows/ci-cd-pipeline.yml](.github/workflows/ci-cd-pipeline.yml) - Added load-tests job
