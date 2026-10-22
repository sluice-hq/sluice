#!/bin/sh
set -eu

# Local containers never attach the exporter or contact Azure. Hosted configuration opts in.
if [ -n "${APPLICATIONINSIGHTS_CONNECTION_STRING:-}" ]; then
  exec java -javaagent:/app/applicationinsights-agent.jar -jar /app/sluice.jar "$@"
fi
exec java -jar /app/sluice.jar "$@"
