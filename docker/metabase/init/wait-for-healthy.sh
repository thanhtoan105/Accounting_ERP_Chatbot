#!/bin/bash
set -e

echo "=============================================="
echo "Metabase Health Check"
echo "=============================================="

METABASE_URL="${METABASE_URL:-http://localhost:3000}"
MAX_ATTEMPTS=24
ATTEMPT=0

echo "Waiting for Metabase to be healthy..."
echo "URL: $METABASE_URL"

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    echo "Attempt $ATTEMPT/$MAX_ATTEMPTS..."
    
    if curl -sf "$METABASE_URL/api/health" > /dev/null 2>&1; then
        RESPONSE=$(curl -s "$METABASE_URL/api/health")
        echo ""
        echo "✓ Metabase is healthy!"
        echo "Response: $RESPONSE"
        echo ""
        echo "Access Metabase at: $METABASE_URL"
        exit 0
    fi
    
    sleep 5
done

echo ""
echo "✗ Metabase did not become healthy within 2 minutes"
echo ""
echo "Troubleshooting:"
echo "  1. Check logs: docker compose logs metabase"
echo "  2. Check metabase-db: docker compose ps metabase-db"
echo "  3. Check memory usage: docker stats"
exit 1
