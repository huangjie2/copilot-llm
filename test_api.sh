#!/bin/bash
# Quick test script for Copilot Proxy API

BASE_URL="${1:-http://localhost:8080}"

echo "========================================"
echo "Testing Copilot Proxy API"
echo "Base URL: $BASE_URL"
echo "========================================"
echo

# Check auth status
echo "1. Checking auth status..."
curl -s "${BASE_URL}/v1/auth/status" | jq .
echo

# List models
echo "2. Listing available models..."
curl -s "${BASE_URL}/v1/models" | jq .
echo

# Test chat completion (will fail if not authenticated)
echo "3. Testing chat completion..."
curl -s -X POST "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o","messages":[{"role":"user","content":"Say hello in 3 words"}],"max_tokens":20}' | jq .
echo

# Test embeddings (will fail if not authenticated)
echo "4. Testing embeddings..."
curl -s -X POST "${BASE_URL}/v1/embeddings" \
  -H "Content-Type: application/json" \
  -d '{"model":"text-embedding-ada-002","input":"Hello world"}' | jq .
echo

echo "========================================"
echo "Test completed"
echo "========================================"
