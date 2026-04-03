package com.lugality.scraper.service;

import com.lugality.scraper.config.ScraperSettings;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Slf4j
@Component
@Scope("prototype")
public class BrowserManager {

    private final ScraperSettings settings;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    @Getter
    private Page page;

    @Getter
    private int workerId = 0;

    private final Path debugDir    = Paths.get("./output/debug");
    private final Path downloadDir = Paths.get("./output/downloads");

    @Autowired
    public BrowserManager(ScraperSettings settings) {
        this.settings = settings;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public Page start() {
        try {
            debugDir.toFile().mkdirs();
            downloadDir.toFile().mkdirs();

            log.info("[Worker {}] Starting browser (headless={})...", workerId, settings.isHeadless());

            playwright = Playwright.create();

            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(settings.isHeadless())
                    .setArgs(Arrays.asList(
                            "--no-sandbox",
                            "--disable-setuid-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-blink-features=AutomationControlled",
                            "--disable-gpu",
                            "--single-process",
                            // ── Memory optimization args ──────────────────
                            "--disable-extensions",
                            "--disable-plugins",
                            "--disable-background-networking",
                            "--disable-background-timer-throttling",
                            "--disable-backgrounding-occluded-windows",
                            "--disable-breakpad",
                            "--disable-client-side-phishing-detection",
                            "--disable-component-update",
                            "--disable-default-apps",
                            "--disable-domain-reliability",
                            "--disable-features=AudioServiceOutOfProcess",
                            "--disable-hang-monitor",
                            "--disable-ipc-flooding-protection",
                            "--disable-notifications",
                            "--disable-offer-store-unmasked-wallet-cards",
                            "--disable-popup-blocking",
                            "--disable-print-preview",
                            "--disable-prompt-on-repost",
                            "--disable-renderer-backgrounding",
                            "--disable-speech-api",
                            "--disable-sync",
                            "--hide-scrollbars",
                            "--ignore-gpu-blacklist",
                            "--metrics-recording-only",
                            "--mute-audio",
                            "--no-first-run",
                            "--no-pings",
                            "--no-zygote",
                            "--password-store=basic",
                            "--use-gl=swiftshader",
                            "--use-mock-keychain",
                            "--js-flags=--max-old-space-size=128"
                    ));

            browser = playwright.chromium().launch(launchOptions);

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setViewportSize(1280, 800)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setAcceptDownloads(true);

            context = browser.newContext(contextOptions);
            context.setDefaultTimeout(settings.getBrowserTimeout());

            page = context.newPage();

            log.info("[Worker {}] Browser started successfully", workerId);
            return page;

        } catch (Exception e) {
            stop();
            throw new RuntimeException("[Worker " + workerId + "] Failed to start browser: " + e.getMessage(), e);
        }
    }

    public void stop() {
        log.info("[Worker {}] Stopping browser...", workerId);
        try { if (page      != null) { page.close();      page      = null; } } catch (Exception ignored) {}
        try { if (context   != null) { context.close();   context   = null; } } catch (Exception ignored) {}
        try { if (browser   != null) { browser.close();   browser   = null; } } catch (Exception ignored) {}
        try { if (playwright != null) { playwright.close(); playwright = null; } } catch (Exception ignored) {}
    }

    public boolean isRunning() {
        return page != null;
    }

    public boolean isAlive() {
        if (page == null) return false;
        try {
            page.title();
            return true;
        } catch (Exception e) {
            log.warn("[Worker {}] isAlive() probe failed: {}", workerId, e.getMessage());
            return false;
        }
    }

    public void navigate(String url) {
        log.info("[Worker {}] Navigating to: {}", workerId, url);
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60_000));
        } catch (Exception e) {
            log.warn("[Worker {}] Navigate attempt 1 failed: {} — retrying with 90s timeout",
                    workerId, e.getMessage());
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(90_000));
        }
    }

    public Path screenshot(String name) {
        try {
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path path = debugDir.resolve(name + "_w" + workerId + "_" + ts + ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            log.debug("[Worker {}] Screenshot: {}", workerId, path);
            return path;
        } catch (Exception e) {
            log.warn("[Worker {}] Screenshot failed: {}", workerId, e.getMessage());
            return null;
        }
    }

    public java.util.Map<String, String> getCookies() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        context.cookies().forEach(c -> m.put(c.name, c.value));
        return m;
    }
}
