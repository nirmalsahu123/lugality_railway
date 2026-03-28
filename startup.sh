#!/bin/bash

echo "🚀 Starting Spring Boot application..."
java \
    -Xms256m -Xmx768m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -jar app.jar &

APP_PID=$!

echo "⏳ Waiting for app to be ready on port 8080..."
until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; do
    sleep 2
    echo "   Still waiting..."
done

echo "✅ App is ready! Firing batch scrape job..."

# Read applications.json and send POST request
APPS=$(cat /app/applications.json)

curl -X POST http://localhost:8080/api/scrape/batch \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL_ADDRESS}\",\"applications\":${APPS},\"workers\":${NUM_WORKERS:-4},\"resume\":false}" \
    | python3 -m json.tool || echo "Batch job triggered!"

echo "🎯 Scraping started! Waiting for app to finish..."
wait $APP_PID
