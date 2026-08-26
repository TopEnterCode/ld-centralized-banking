package com.example.banking.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "e2e", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrowserJourneyE2ETest {
    private static Playwright playwright;
    private static Browser browser;
    private static String baseUrl;

    @BeforeAll
    static void beforeAll() {
        baseUrl = System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080");
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void afterAll() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    @Order(1)
    void clientFlagIndividualAndSegmentsChangeBrowserUi() {
        Page page = newPage(1440, 900);
        page.navigate(baseUrl);
        assertThat(page.locator("#mode-badge")).hasText("MOCK MODE");
        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        assertThat(page.locator("#ui-version-chip")).hasText("LEGACY UI");

        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Target individual")));
        assertThat(page.locator("#ui-version-chip")).hasText("NEW UI");
        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        page.selectOption("#persona-select", "somchai-employee");
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Employee segment")));
        assertThat(page.locator("#ui-version-chip")).hasText("NEW UI");
        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        page.selectOption("#persona-select", "mali-pilot");
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pilot segment")));
        assertThat(page.locator("#ui-version-chip")).hasText("NEW UI");
        page.close();
    }

    @Test
    @Order(2)
    void percentageAssignmentIsStableAndExactCountsAreVisible() {
        Page page = newPage(1440, 900);
        page.navigate(baseUrl);
        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        Locator tenPercent =
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("10%").setExact(true));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("0%").setExact(true)));
        assertThat(page.locator("#enabled-count")).hasText("0");
        clickControl(page, tenPercent);
        assertThat(page.locator("#enabled-count")).hasText("9");
        String first = page.locator("#enabled-count").textContent();
        clickControl(page, tenPercent);
        assertThat(page.locator("#enabled-count").textContent()).isEqualTo(first);
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("50%").setExact(true)));
        assertThat(page.locator("#enabled-count")).hasText("44");
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("100%").setExact(true)));
        assertThat(page.locator("#enabled-count")).hasText("100");
        assertThat(page.locator("#rollout-grid span")).hasCount(100);
        page.close();
    }

    @Test
    @Order(3)
    void migrationKillSwitchAndCompleteJourneyAreVisible() {
        Page page = newPage(1440, 900);
        page.navigate(baseUrl);
        for (String stage : new String[] {"Off", "Shadow", "Live", "Complete"}) {
            clickControl(
                    page,
                    page.getByRole(
                            AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName(stage).setExact(true)));
            page.locator("#submit-payment").click();
            assertThat(page.locator("#payment-result")).isVisible();
            String expected = stage.equals("Live") || stage.equals("Complete") ? "V2" : "V1";
            assertThat(page.locator("#result-version")).hasText(expected);
            assertThat(page.locator("#timeline .timeline-row")).hasCount(6);
            page.locator("#new-payment").click();
        }
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Live").setExact(true)));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName("Activate kill switch")
                                .setExact(true)));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#result-version")).hasText("V1");
        assertThat(page.locator("#timeline")).containsText("v2-disabled-by-kill-switch");
        page.locator("#new-payment").click();
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName("Deactivate kill switch")
                                .setExact(true)));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#result-version")).hasText("V2");
        page.close();
    }

    @Test
    @Order(4)
    void failuresProduceHonestDegradedTimeline() {
        Page page = newPage(1440, 900);
        page.navigate(baseUrl);
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Restore all services")));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Simulate LD failure")));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#timeline")).containsText("sdk-default");
        page.locator("#new-payment").click();
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Restore all services")));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Simulate DTM timeout")));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#timeline")).containsText("service-fallback");
        page.locator("#new-payment").click();
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Restore all services")));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Simulate DTM failure")));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#timeline")).containsText("service-fallback");
        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        assertThat(page.locator("#payment-panel")).isVisible();
        assertThat(page.locator("#payment-result")).isHidden();
        assertThat(page.locator("#timeline")).containsText("Run a synthetic payment");
        assertThat(page.locator("#correlation-id")).hasText("No journey yet");
        page.close();
    }

    @Test
    @Order(5)
    void downstreamFailureControlsExposeSafeBehavior() {
        Page page = newPage(1440, 900);
        page.navigate(baseUrl);
        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Complete").setExact(true)));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Payment v2 failure")));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#result-version")).hasText("V1");
        assertThat(page.locator("#timeline")).containsText("v2-failed-safe-fallback");

        clickControl(
                page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset demo")));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Target individual")));
        clickControl(
                page,
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Provider B failure")));
        page.locator("#submit-payment").click();
        assertThat(page.locator("#timeline")).containsText("queued-after-provider-b-failure");
        assertThat(page.locator("#timeline")).containsText("service-fallback");
        page.close();
    }

    @Test
    @Order(6)
    void capturesRequiredPresentationViewportsWithoutHorizontalOverflow() throws Exception {
        Files.createDirectories(Path.of("screenshots"));
        for (int[] viewport : new int[][] {{1440, 900}, {1366, 768}}) {
            Page page = newPage(viewport[0], viewport[1]);
            page.navigate(baseUrl);
            clickControl(
                    page,
                    page.getByRole(
                            AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("Restore all services")));
            assertThat(page.locator("body").evaluate("el => el.scrollWidth <= el.clientWidth"))
                    .isEqualTo(true);
            page.screenshot(
                    new Page.ScreenshotOptions()
                            .setPath(
                                    Path.of(
                                            "screenshots/poc-%dx%d.png"
                                                    .formatted(viewport[0], viewport[1])))
                            .setFullPage(true));
            page.close();
        }
    }

    private static Page newPage(int width, int height) {
        BrowserContext context =
                browser.newContext(
                        new Browser.NewContextOptions()
                                .setViewportSize(width, height)
                                .setReducedMotion(
                                        com.microsoft.playwright.options.ReducedMotion.REDUCE));
        Page page = context.newPage();
        page.setDefaultTimeout(10000);
        return page;
    }

    private static void clickControl(Page page, Locator button) {
        page.waitForResponse(
                response ->
                        response.url().endsWith("/api/demo/control") && response.status() == 200,
                button::click);
    }
}
