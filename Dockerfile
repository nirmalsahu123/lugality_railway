# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn clean package -DskipTests

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# ── System deps for Chromium (Playwright) ─────────────────────────────────────
RUN apt-get update && apt-get install -y \
    wget curl unzip ca-certificates \
    libnss3 libnspr4 \
    libdbus-1-3 \
    libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libdrm2 \
    libxkbcommon0 libxcomposite1 libxdamage1 \
    libxfixes3 libxrandr2 libgbm1 \
    libasound2 libpango-1.0-0 libcairo2 \
    libx11-6 libx11-xcb1 libxcb1 libxext6 \
    fonts-liberation libfontconfig1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# ── Install Playwright Chromium ────────────────────────────────────────────────
# PLAYWRIGHT_BROWSERS_PATH must be set BEFORE the install command.
# We use /ms-playwright so it's baked into the image layer.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Extract the Playwright CLI from the fat jar and run install.
# The fat jar includes com.microsoft.playwright.CLI — run it directly.
RUN java -cp app.jar com.microsoft.playwright.CLI install chromium \
    && echo "Chromium installed at $PLAYWRIGHT_BROWSERS_PATH"

# Verify the browser binary exists (build fails fast if install silently failed)
RUN find /ms-playwright -name "chrome" -o -name "chromium" | grep . || \
    (echo "ERROR: Chromium binary not found after install" && exit 1)

# ── Output directories ─────────────────────────────────────────────────────────
RUN mkdir -p /app/output/data /app/output/pdfs /app/output/debug /app/output/downloads

EXPOSE 8080

ENV PORT=8080
ENV HEADLESS=true
# Ensure Playwright finds the browsers even if the env is re-exported
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Tune JVM for Railway's constrained memory (512MB–1GB typical)
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx768m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-jar", "app.jar"]
