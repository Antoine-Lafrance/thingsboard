#!/bin/bash
#
# Copyright © 2016-2026 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Convert Gatling HTML results to Markdown summary for GitHub Actions

GATLING_RESULTS_DIR="target/gatling"

echo "# 📊 Load Test Results"
echo ""

# Find latest Gatling report
LATEST_REPORT=$(find $GATLING_RESULTS_DIR -name "index.html" -type f | sort -r | head -n 1)

if [ -z "$LATEST_REPORT" ]; then
    echo "❌ No Gatling results found"
    exit 1
fi

REPORT_DIR=$(dirname "$LATEST_REPORT")
STATS_FILE="$REPORT_DIR/js/stats.json"

if [ -f "$STATS_FILE" ]; then
    echo "## Summary Statistics"
    echo ""
    
    # Extract key metrics (simplified parsing - in production use jq)
    TOTAL_REQUESTS=$(grep -o '"numberOfRequests":{"total":[0-9]*' "$STATS_FILE" | head -1 | grep -o '[0-9]*$')
    SUCCESS_REQUESTS=$(grep -o '"numberOfRequests":{"total":[0-9]*,"ok":[0-9]*' "$STATS_FILE" | head -1 | grep -o '[0-9]*$')
    MEAN_RESPONSE=$(grep -o '"meanResponseTime":{"total":[0-9]*' "$STATS_FILE" | head -1 | grep -o '[0-9]*$')
    P95_RESPONSE=$(grep -o '"percentiles95":{"total":[0-9]*' "$STATS_FILE" | head -1 | grep -o '[0-9]*$')
    
    echo "| Metric | Value |"
    echo "|--------|-------|"
    echo "| Total Requests | ${TOTAL_REQUESTS:-N/A} |"
    echo "| Successful Requests | ${SUCCESS_REQUESTS:-N/A} |"
    echo "| Mean Response Time | ${MEAN_RESPONSE:-N/A}ms |"
    echo "| P95 Response Time | ${P95_RESPONSE:-N/A}ms |"
    echo ""
fi

echo "## 📁 Full Report"
echo ""
echo "Download the Gatling HTML report from workflow artifacts for detailed analysis."
echo ""
echo "Report location: \`$REPORT_DIR\`"
