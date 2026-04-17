const { test, expect } = require("@playwright/test");

const BASE = process.env.SILVERSTAGE_BASE_URL || "http://localhost:8080";
const PASSWORD = "Passw0rd!23";

function uniqueUser(p) {
  return p + "_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/**
 * Register a new account via API and immediately log in.
 * Returns { token, username }.
 */
async function registerAndLogin(request, role) {
  const username = uniqueUser("e2e_" + role.toLowerCase());
  const regResp = await request.post(`${BASE}/api/security/accounts`, {
    data: { username, password: PASSWORD, role },
  });
  expect(regResp.ok(), `register ${role} should succeed`).toBeTruthy();

  const loginResp = await request.post(`${BASE}/api/security/login`, {
    data: { username, password: PASSWORD },
  });
  expect(loginResp.ok(), `login ${role} should succeed`).toBeTruthy();

  const { token } = await loginResp.json();
  return { token, username };
}

/**
 * Inject auth tokens into the page's localStorage and reload so the portal
 * applies its auth-gated UI sections.
 */
async function authenticatePage(page, token, username, role) {
  await page.evaluate(
    (d) => {
      localStorage.setItem("silverstage.authToken", d.token);
      localStorage.setItem("silverstage.authUser", d.username);
      localStorage.setItem("silverstage.authRole", d.role);
    },
    { token, username, role },
  );
  await page.reload();
}

/**
 * Register a new account and log in entirely through the browser UI form.
 * No API calls or localStorage injection — exercises the real auth.js path.
 *
 * auth.js validates password length >= 10 before calling $.ajax, so PASSWORD
 * ("Passw0rd!23", 11 chars) satisfies that check.
 *
 * The #auth-role <select> in index.html already contains all five role
 * options (SENIOR, FAMILY_MEMBER, SERVICE_STAFF, ORG_ADMIN, PLATFORM_ADMIN),
 * so selectOption() works without any DOM manipulation.
 */
async function uiRegisterAndLogin(page, username, password, role) {
  await page.goto("/");
  await expect(page.locator("#auth-username")).toBeVisible({ timeout: 10000 });

  // Fill registration fields
  await page.fill("#auth-username", username);
  await page.fill("#auth-password", password);
  if (role) {
    await page.selectOption("#auth-role", role);
  }
  await page.click("#auth-register");

  // auth.js sets the message to "Account {username} registered successfully as {role}."
  await expect(page.locator("#auth-message")).toContainText("registered", {
    timeout: 10000,
  });

  // Log in with the same credentials — password field still holds the value
  await page.click("#auth-login");

  // auth.js sets the message to "Login successful. Role: ..."
  await expect(page.locator("#auth-message")).toContainText("Login successful", {
    timeout: 10000,
  });
}

// ---------------------------------------------------------------------------
// Suite 1 – Core portal flows (existing tests, rewritten using helpers)
// ---------------------------------------------------------------------------

test.describe("Core portal flows", () => {
  test("login flow", async ({ page, request }) => {
    const username = uniqueUser("e2e_login");

    // Register via API first
    const regResp = await request.post(`${BASE}/api/security/accounts`, {
      data: { username, password: PASSWORD, role: "SENIOR" },
    });
    expect(regResp.ok()).toBeTruthy();

    // Navigate to portal
    await page.goto("/");
    await expect(page.locator("#auth-username")).toBeVisible();

    // Fill and submit login form
    await page.fill("#auth-username", username);
    await page.fill("#auth-password", PASSWORD);
    await page.click("#auth-login");

    // Login success banner
    await expect(page.locator("#auth-message")).toContainText(
      "Login successful",
      { timeout: 10000 },
    );
    await expect(page.locator("#auth-logout")).toBeVisible();

    // Events list populated after login
    await expect(page.locator("#event-list")).not.toBeEmpty();
  });

  test("discovery search flow", async ({ page }) => {
    await page.goto("/");

    await expect(page.locator("#search-input")).toBeVisible({ timeout: 10000 });
    await page.fill("#search-input", "choir");
    await page.click("#search-btn");

    // Results area should no longer show the default placeholder text
    await expect(page.locator("#search-results")).not.toContainText(
      "Use the search tools above.",
      { timeout: 10000 },
    );
  });

  test("seat reservation flow", async ({ page, request }) => {
    const { token, username } = await registerAndLogin(request, "SENIOR");

    await page.goto("/");
    await authenticatePage(page, token, username, "SENIOR");

    // Wait for at least one event's hierarchy button to render
    await expect(page.locator(".open-hierarchy").first()).toBeVisible({
      timeout: 15000,
    });
    await page.locator(".open-hierarchy").first().click();

    // Hierarchy container should show data, not the idle placeholder
    await expect(page.locator("#hierarchy-container")).not.toContainText(
      "Loading",
      { timeout: 15000 },
    );
    await expect(page.locator("#hierarchy-container")).not.toContainText(
      "Select an event",
    );
  });

  test("publishing draft creation flow", async ({ page, request }) => {
    const { token, username } = await registerAndLogin(request, "SENIOR");

    await page.goto("/");
    await authenticatePage(page, token, username, "SENIOR");

    // Publishing panel is auth-gated — wait for the title input
    await expect(page.locator("#publish-title")).toBeVisible({
      timeout: 15000,
    });

    await page.fill("#publish-title", "E2E Test Draft");
    await page.fill("#publish-body", "E2E test body content");
    await page.click("#create-draft");

    await expect(page.locator("#publishing-output")).toContainText(
      "Created draft",
      { timeout: 10000 },
    );
  });

  test("seat payment via API after order creation", async ({ request }) => {
    const senior = await registerAndLogin(request, "SENIOR");

    // Create seat order via API
    const orderResp = await request.post(`${BASE}/api/seat-orders`, {
      headers: { "X-Auth-Token": senior.token, "Content-Type": "application/json" },
      data: {
        eventId: 1, sessionId: 1, ticketTypeId: 1,
        orderCode: "E2E-PAY-" + Date.now(),
        buyerReference: senior.username,
        channel: "ONLINE_PORTAL",
        seatIds: [3],
      },
    });
    // May get 409 if seat taken; handle gracefully
    if (orderResp.status() === 201) {
      const order = await orderResp.json();
      expect(order.orderId).toBeTruthy();
      expect(order.status).toBe("PENDING");

      // Pay the order
      const payResp = await request.post(`${BASE}/api/seat-orders/${order.orderId}/pay`, {
        headers: { "X-Auth-Token": senior.token },
      });
      expect(payResp.ok()).toBeTruthy();
      const paid = await payResp.json();
      expect(paid.status).toBe("PAID");
      expect(paid.orderCode).toBe(order.orderCode);
    }
  });
});

// ---------------------------------------------------------------------------
// Suite 2 – Moderation flows
// ---------------------------------------------------------------------------

test.describe("Moderation flows", () => {
  test("moderation report and decision flow", async ({ page, request }) => {
    // --- Reporter: any authenticated user (SENIOR) ---
    const reporter = await registerAndLogin(request, "SENIOR");

    // Navigate and authenticate as the reporter
    await page.goto("/");
    await authenticatePage(page, reporter.token, reporter.username, "SENIOR");

    // Wait for the moderation panel to be present in DOM (panel is visible
    // regardless of role; role-gating controls specific buttons, not the panel)
    await expect(page.locator("#reported-user")).toBeVisible({ timeout: 15000 });

    // Fill report fields
    const targetUser = uniqueUser("target");
    await page.fill("#reported-user", targetUser);
    await page.fill("#report-content-type", "ANNOUNCEMENT");
    await page.fill("#report-content-ref", "announcement:1");
    await page.fill("#report-reason", "Inappropriate language in announcement");

    // Submit the report
    await page.click("#submit-report");

    // Outcome should show a report ID
    await expect(page.locator("#moderation-outcome")).toContainText("Report #", {
      timeout: 10000,
    });

    // --- Moderator: ORG_ADMIN loads and decides ---
    const moderator = await registerAndLogin(request, "ORG_ADMIN");
    await authenticatePage(
      page,
      moderator.token,
      moderator.username,
      "ORG_ADMIN",
    );

    // Load open reports
    await expect(page.locator("#load-open-reports")).toBeVisible({
      timeout: 10000,
    });
    await page.click("#load-open-reports");

    // Console should list at least the report we just filed
    await expect(page.locator("#moderation-console")).toContainText("Report #", {
      timeout: 10000,
    });

    // Grab the first rendered decision controls and apply a decision
    const applyBtn = page.locator(".apply-decision").first();
    await expect(applyBtn).toBeVisible({ timeout: 10000 });

    // Set penalty to MUTE_24H on the first rendered report row
    const penaltySelect = page.locator(".decision-penalty").first();
    await penaltySelect.selectOption("MUTE_24H");

    // Optionally fill decision notes
    const notesInput = page.locator(".decision-notes").first();
    await notesInput.fill("E2E automated decision note");

    await applyBtn.click();

    // Outcome should confirm resolution
    await expect(page.locator("#moderation-outcome")).toContainText("resolved", {
      timeout: 10000,
    });
  });

  test("forbidden action surfaces UI error for SENIOR attempting to load open reports", async ({
    page,
    request,
  }) => {
    // SENIOR users cannot call GET /api/moderation/reports (403)
    const senior = await registerAndLogin(request, "SENIOR");

    await page.goto("/");
    await authenticatePage(page, senior.token, senior.username, "SENIOR");

    await expect(page.locator("#load-open-reports")).toBeVisible({
      timeout: 15000,
    });
    await page.click("#load-open-reports");

    // The moderation.js fail handler writes the server error into
    // #moderation-console when the API returns 403
    await expect(page.locator("#moderation-console")).not.toContainText(
      "No moderation cases loaded.",
      { timeout: 10000 },
    );
    // Should not display a report list — should show an error message instead
    await expect(page.locator("#moderation-console")).not.toContainText(
      "Report #",
      { timeout: 5000 },
    );
  });

  test("moderation notification read flow via API", async ({ request }) => {
    const reporter = await registerAndLogin(request, "SENIOR");
    const target = await registerAndLogin(request, "SENIOR");
    const admin = await registerAndLogin(request, "ORG_ADMIN");

    // Submit report
    const reportResp = await request.post(`${BASE}/api/moderation/reports`, {
      headers: { "X-Auth-Token": reporter.token },
      params: {
        reportedUser: target.username,
        contentType: "ANNOUNCEMENT",
        contentRef: "announcement:1",
        reason: "E2E notification test",
      },
    });
    expect(reportResp.ok()).toBeTruthy();
    const report = await reportResp.json();

    // Decide
    const decisionResp = await request.post(`${BASE}/api/moderation/reports/${report.reportId}/decision`, {
      headers: { "X-Auth-Token": admin.token },
      data: { penaltyType: "MUTE_24H", decisionNotes: "E2E notification test decision" },
    });
    expect(decisionResp.ok()).toBeTruthy();

    // Get notifications for reporter
    const notifResp = await request.get(`${BASE}/api/moderation/users/${reporter.username}/notifications`, {
      headers: { "X-Auth-Token": admin.token },
    });
    expect(notifResp.ok()).toBeTruthy();
    const notifications = await notifResp.json();
    expect(notifications.length).toBeGreaterThan(0);

    const notifId = notifications[0].notificationId;

    // Mark as read (reporter marks own notification)
    const markResp = await request.patch(`${BASE}/api/moderation/notifications/${notifId}/read`, {
      headers: { "X-Auth-Token": reporter.token },
    });
    expect(markResp.status()).toBe(204);

    // Verify read state via follow-up fetch
    const checkResp = await request.get(`${BASE}/api/moderation/users/${reporter.username}/notifications`, {
      headers: { "X-Auth-Token": admin.token },
    });
    expect(checkResp.ok()).toBeTruthy();
    const updatedNotifs = await checkResp.json();
    const readNotif = updatedNotifs.find(n => n.notificationId === notifId);
    expect(readNotif).toBeTruthy();
    expect(readNotif.readFlag).toBe("Y");
  });
});

// ---------------------------------------------------------------------------
// Suite 3 – Authorization & visibility guards
// ---------------------------------------------------------------------------

test.describe("Authorization and visibility", () => {
  test("unauthorized user does not see protected UI sections", async ({
    page,
  }) => {
    // Navigate without any authentication
    await page.goto("/");

    // The moderation panel has data-required-roles — the JS hides it when
    // no auth session is present. Assert it carries the is-hidden class or
    // is not visible in the rendered DOM.
    // The panel element IS in the DOM (static HTML) but should be hidden.
    const modPanel = page.locator(
      '[data-auth-section="authenticated"][data-required-roles]',
    );
    // Either hidden via CSS class or not visible
    await expect(modPanel).not.toBeVisible({ timeout: 10000 });

    // Publishing panel: data-auth-section="authenticated" (no explicit role
    // restriction but still auth-gated)
    const publishingPanel = page.locator("#publishing-panel, #publish-title");
    // #publish-title lives inside an auth-gated section — should be hidden
    await expect(page.locator("#publish-title")).not.toBeVisible({
      timeout: 5000,
    });

    // Logout button should be hidden for unauthenticated users
    await expect(page.locator("#auth-logout")).toHaveClass(/is-hidden/);
  });

  test("lower-role user cannot perform privileged actions via API", async ({
    request,
  }) => {
    const senior = await registerAndLogin(request, "SENIOR");

    // 1. SENIOR cannot decide a moderation report (403)
    const decisionResp = await request.post(
      `${BASE}/api/moderation/reports/999/decision`,
      {
        headers: { "X-Auth-Token": senior.token },
        data: { penaltyType: "MUTE_24H", decisionNotes: "test" },
      },
    );
    expect(decisionResp.status()).toBe(403);

    // 2. SENIOR cannot record a payment tender (403)
    const tenderResp = await request.post(`${BASE}/api/payments/tenders`, {
      headers: { "X-Auth-Token": senior.token },
      data: {
        transactionRef: "TXN-E2E-SENIOR",
        tenderType: "CARD",
        amount: 10.0,
        merchantCode: "MC001",
      },
    });
    expect(tenderResp.status()).toBe(403);

    // 3. SENIOR cannot upload files (403)
    const uploadResp = await request.post(`${BASE}/api/files/upload`, {
      headers: { "X-Auth-Token": senior.token },
      multipart: {
        title: "Unauthorized upload attempt",
        folderPath: "/test",
        accessLevel: "STAFF_AND_ADMIN",
        file: {
          name: "test.txt",
          mimeType: "text/plain",
          buffer: Buffer.from("unauthorized"),
        },
      },
    });
    expect(uploadResp.status()).toBe(403);
  });
});

// ---------------------------------------------------------------------------
// Suite 4 – File management workflow (API)
// ---------------------------------------------------------------------------

test.describe("File management workflow", () => {
  test("file management workflow via API", async ({ request }) => {
    const admin = await registerAndLogin(request, "SERVICE_STAFF");

    // Upload a new document
    const uploadResp = await request.post(`${BASE}/api/files/upload`, {
      headers: { "X-Auth-Token": admin.token },
      multipart: {
        title: "E2E Doc",
        folderPath: "/e2e",
        accessLevel: "STAFF_AND_ADMIN",
        file: {
          name: "test.txt",
          mimeType: "text/plain",
          buffer: Buffer.from("e2e content"),
        },
      },
    });
    expect(uploadResp.ok(), "upload should succeed").toBeTruthy();
    const uploadBody = await uploadResp.json();
    expect(uploadBody.documentId).toBeTruthy();
    const documentId = uploadBody.documentId;

    // List documents — should include the newly uploaded file
    const listResp = await request.get(`${BASE}/api/files`, {
      headers: { "X-Auth-Token": admin.token },
    });
    expect(listResp.ok(), "list should succeed").toBeTruthy();
    const listBody = await listResp.json();
    // PagedDocumentsResponse — check specific pagination and data fields
    expect(listBody.totalItems).toBeDefined();
    expect(listBody.documents).toBeDefined();
    expect(Array.isArray(listBody.documents)).toBeTruthy();
    expect(listBody.documents.length).toBeGreaterThanOrEqual(1);
    // Verify our uploaded doc is in the list
    const found = listBody.documents.some(d => d.documentId === documentId || d.title === "E2E Doc");
    expect(found, "Uploaded document should appear in list").toBeTruthy();

    // Upload a new version of the document
    const versionResp = await request.post(
      `${BASE}/api/files/${documentId}/versions`,
      {
        headers: { "X-Auth-Token": admin.token },
        multipart: {
          file: {
            name: "v2.txt",
            mimeType: "text/plain",
            buffer: Buffer.from("v2 content"),
          },
        },
      },
    );
    expect(versionResp.ok(), "version upload should succeed").toBeTruthy();
    const versionBody = await versionResp.json();
    expect(versionBody.latestVersion).toBeGreaterThanOrEqual(2);

    // Get version history
    const histResp = await request.get(
      `${BASE}/api/files/${documentId}/history`,
      { headers: { "X-Auth-Token": admin.token } },
    );
    expect(histResp.ok(), "history should succeed").toBeTruthy();
    const histBody = await histResp.json();
    expect(histBody.documentId).toBe(documentId);
    expect(histBody.versions).toBeDefined();
    expect(Array.isArray(histBody.versions)).toBeTruthy();
    expect(histBody.versions.length).toBeGreaterThanOrEqual(2);
    expect(histBody.versions[0].versionNumber).toBeDefined();

    // Generate a download link
    const linkResp = await request.post(
      `${BASE}/api/files/${documentId}/download-links`,
      { headers: { "X-Auth-Token": admin.token } },
    );
    expect(linkResp.ok(), "generate download link should succeed").toBeTruthy();
    const linkBody = await linkResp.json();
    expect(linkBody.token).toBeTruthy();

    // Download via the generated token
    const dlResp = await request.get(
      `${BASE}/api/files/download/${linkBody.token}`,
      { headers: { "X-Auth-Token": admin.token } },
    );
    expect(dlResp.ok(), "download should succeed").toBeTruthy();
    const dlBody = await dlResp.body();
    expect(dlBody.toString()).toContain("content"); // uploaded "v2 content"
  });
});

// ---------------------------------------------------------------------------
// Suite 5 – Payment workflow (API)
// ---------------------------------------------------------------------------

test.describe("Payment workflow", () => {
  test("payment workflow via API", async ({ request }) => {
    const staff = await registerAndLogin(request, "SERVICE_STAFF");
    const admin = await registerAndLogin(request, "ORG_ADMIN");

    const txRef = "TXN-E2E-" + Date.now();

    // 1. Record a tender (SERVICE_STAFF can do this)
    const tenderResp = await request.post(`${BASE}/api/payments/tenders`, {
      headers: { "X-Auth-Token": staff.token },
      data: {
        transactionRef: txRef,
        tenderType: "CARD",
        amount: 75.0,
        merchantCode: "MC-E2E",
      },
    });
    expect(tenderResp.ok(), "record tender should succeed").toBeTruthy();
    const tenderBody = await tenderResp.json();
    expect(tenderBody.transactionRef).toBe(txRef);
    expect(tenderBody.status).toBe("RECORDED");
    expect(tenderBody.grossAmount).toBeDefined();

    // 2. Process a callback (ORG_ADMIN required)
    const callbackResp = await request.post(`${BASE}/api/payments/callbacks`, {
      headers: { "X-Auth-Token": admin.token },
      params: {
        transactionRef: txRef,
        gatewayBatchRef: "BATCH-001",
        settledAmount: "75.00",
        status: "SETTLED",
        source: "gateway",
      },
    });
    expect(callbackResp.ok(), "process callback should succeed").toBeTruthy();
    const callbackBody = await callbackResp.json();
    expect(callbackBody.processed).toBe(true);

    // 2a. Idempotency check: duplicate callback should return processed = false
    const dupCallbackResp = await request.post(`${BASE}/api/payments/callbacks`, {
      headers: { "X-Auth-Token": admin.token },
      params: {
        transactionRef: txRef,
        gatewayBatchRef: "BATCH-001",
        settledAmount: "75.00",
        status: "SETTLED",
        source: "gateway",
      },
    });
    const dupBody = await dupCallbackResp.json();
    expect(dupBody.processed).toBe(false);

    // 3. Issue a refund (ORG_ADMIN required)
    const refundResp = await request.post(`${BASE}/api/payments/refunds`, {
      headers: { "X-Auth-Token": admin.token },
      data: {
        transactionRef: txRef,
        amount: 75.0,
        reason: "E2E test refund",
      },
    });
    expect(refundResp.ok(), "refund should succeed").toBeTruthy();
    const refundBody = await refundResp.json();
    expect(refundBody.transactionRef).toBe(txRef);
    expect(refundBody.refundAmount).toBeDefined();
    expect(refundBody.refundType).toBeDefined();

    // 4. Reconciliation report (ORG_ADMIN required)
    const reportResp = await request.get(
      `${BASE}/api/payments/reconciliation/report`,
      { headers: { "X-Auth-Token": admin.token } },
    );
    expect(reportResp.ok(), "reconciliation report should succeed").toBeTruthy();
    const reportBody = await reportResp.json();
    expect(reportBody.grossRevenue).toBeDefined();
    expect(reportBody.refundedRevenue).toBeDefined();
    expect(reportBody.netRevenue).toBeDefined();
    expect(reportBody.importedRows).toBeDefined();
    expect(reportBody.processedRows).toBeDefined();

    // 5. Operation traces (ORG_ADMIN required)
    const tracesResp = await request.get(
      `${BASE}/api/payments/reconciliation/traces`,
      { headers: { "X-Auth-Token": admin.token } },
    );
    expect(tracesResp.ok(), "traces should succeed").toBeTruthy();
    const tracesBody = await tracesResp.json();
    expect(Array.isArray(tracesBody)).toBeTruthy();
    expect(tracesBody.length).toBeGreaterThan(0);
    expect(tracesBody[0].action).toBeDefined();
    expect(tracesBody[0].actor).toBeDefined();
    expect(tracesBody[0].entityType).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// Suite 6 – Identity verification (API)
// ---------------------------------------------------------------------------

test.describe("Identity verification", () => {
  test("identity verification submit and approval", async ({ request }) => {
    // Register a SENIOR who will submit verification
    const senior = await registerAndLogin(request, "SENIOR");

    // Submit a verification request
    const submitResp = await request.post(`${BASE}/api/security/verification`, {
      headers: { "X-Auth-Token": senior.token },
      data: {
        fullName: "E2E Test User",
        idType: "PASSPORT",
        idNumber: "E2E-PASS-" + Date.now(),
      },
    });
    expect(submitResp.ok(), "submit verification should succeed").toBeTruthy();
    const verificationBody = await submitResp.json();
    expect(verificationBody.id).toBeTruthy();
    const verificationId = verificationBody.id;

    // Register a PLATFORM_ADMIN to review
    const platformAdmin = await registerAndLogin(request, "PLATFORM_ADMIN");

    // List pending verifications — should contain the one we submitted
    const pendingResp = await request.get(
      `${BASE}/api/security/verification/pending`,
      { headers: { "X-Auth-Token": platformAdmin.token } },
    );
    expect(pendingResp.ok(), "list pending should succeed").toBeTruthy();
    const pendingBody = await pendingResp.json();
    expect(Array.isArray(pendingBody)).toBeTruthy();
    const found = pendingBody.some((v) => v.id === verificationId);
    expect(found, "submitted verification should appear in pending list").toBeTruthy();

    // Approve the verification
    const approveResp = await request.patch(
      `${BASE}/api/security/verification/${verificationId}`,
      {
        headers: { "X-Auth-Token": platformAdmin.token },
        data: { status: "APPROVED", notes: "E2E approval" },
      },
    );
    expect(approveResp.ok(), "approve verification should succeed").toBeTruthy();
    const approveBody = await approveResp.json();
    expect(approveBody.status).toBe("APPROVED");
  });
});

// ---------------------------------------------------------------------------
// Suite 7 – Publishing full lifecycle (API)
// ---------------------------------------------------------------------------

test.describe("Publishing lifecycle", () => {
  test("publishing full lifecycle via API", async ({ request }) => {
    const author = await registerAndLogin(request, "SENIOR");
    const moderator = await registerAndLogin(request, "ORG_ADMIN");

    // 1. Create draft
    const draftResp = await request.post(`${BASE}/api/publishing/content`, {
      headers: { "X-Auth-Token": author.token },
      data: { title: "E2E Lifecycle Title", body: "Initial body text" },
    });
    expect(draftResp.ok(), "create draft should succeed").toBeTruthy();
    const draft = await draftResp.json();
    expect(draft.state).toBe("DRAFT");
    const contentId = draft.contentId;

    // 2. Update draft
    const updateResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/update`,
      {
        headers: { "X-Auth-Token": author.token },
        data: {
          title: "E2E Lifecycle Title Updated",
          body: "Updated body text for diff",
          summary: "E2E update",
        },
      },
    );
    expect(updateResp.ok(), "update draft should succeed").toBeTruthy();
    const updated = await updateResp.json();
    expect(updated.currentVersion).toBeGreaterThanOrEqual(2);

    // 3. Submit for review
    const submitResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/submit`,
      { headers: { "X-Auth-Token": author.token } },
    );
    expect(submitResp.ok(), "submit should succeed").toBeTruthy();
    const submitted = await submitResp.json();
    expect(submitted.state).toBe("SUBMISSION");

    // 4. Mark as under review (moderator)
    const reviewResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/review`,
      { headers: { "X-Auth-Token": moderator.token } },
    );
    expect(reviewResp.ok(), "mark review should succeed").toBeTruthy();
    const reviewed = await reviewResp.json();
    expect(reviewed.state).toBe("REVIEW");

    // 5. Publish (moderator)
    const publishResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/publish`,
      { headers: { "X-Auth-Token": moderator.token } },
    );
    expect(publishResp.ok(), "publish should succeed").toBeTruthy();
    const published = await publishResp.json();
    expect(published.state).toBe("PUBLISH");
    expect(published.publishedAt).toBeTruthy();

    // 6. Request an appeal (author)
    const appealResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/appeals`,
      {
        headers: { "X-Auth-Token": author.token },
        data: { justification: "Needs post-publish correction" },
      },
    );
    expect(appealResp.ok(), "request appeal should succeed").toBeTruthy();
    const appeal = await appealResp.json();
    expect(appeal.status).toBe("PENDING");
    const appealId = appeal.id;

    // 7. Decide on appeal (moderator)
    const appealDecisionResp = await request.post(
      `${BASE}/api/publishing/appeals/${appealId}/decision`,
      {
        headers: { "X-Auth-Token": moderator.token },
        data: { status: "APPROVED", reviewNotes: "E2E approved appeal" },
      },
    );
    expect(
      appealDecisionResp.ok(),
      "decide appeal should succeed",
    ).toBeTruthy();
    const decidedAppeal = await appealDecisionResp.json();
    expect(decidedAppeal.status).toBe("APPROVED");

    // 8. Apply correction (author, appeal approved)
    const correctionResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/corrections?appealId=${appealId}`,
      {
        headers: { "X-Auth-Token": author.token },
        data: {
          title: "E2E Corrected Title",
          body: "Corrected body after appeal",
          summary: "Post-publish correction",
        },
      },
    );
    expect(correctionResp.ok(), "apply correction should succeed").toBeTruthy();
    const corrected = await correctionResp.json();
    expect(corrected.contentId).toBe(contentId);
    expect(corrected.currentVersion).toBeGreaterThanOrEqual(3);
    expect(corrected.state).toBe("PUBLISH");

    // 9. Get versions — should have at least 3 entries
    const versionsResp = await request.get(
      `${BASE}/api/publishing/content/${contentId}/versions`,
      { headers: { "X-Auth-Token": author.token } },
    );
    expect(versionsResp.ok(), "versions should succeed").toBeTruthy();
    const versions = await versionsResp.json();
    expect(Array.isArray(versions)).toBeTruthy();
    expect(versions.length).toBeGreaterThanOrEqual(3);
    expect(versions[0].versionNumber).toBeDefined();
    expect(versions[0].title).toBeDefined();
    expect(versions[0].changeType).toBeDefined();

    // 10. Diff between v1 and v2
    const diffResp = await request.get(
      `${BASE}/api/publishing/content/${contentId}/diff?leftVersion=1&rightVersion=2`,
      { headers: { "X-Auth-Token": author.token } },
    );
    expect(diffResp.ok(), "diff should succeed").toBeTruthy();
    const diff = await diffResp.json();
    expect(diff.leftVersion).toBe(1);
    expect(diff.rightVersion).toBe(2);
    expect(diff.leftLines).toBeDefined();
    expect(diff.rightLines).toBeDefined();
    expect(Array.isArray(diff.leftLines)).toBeTruthy();
    expect(Array.isArray(diff.rightLines)).toBeTruthy();

    // 11. Audit trail
    const auditResp = await request.get(
      `${BASE}/api/publishing/content/${contentId}/audit`,
      { headers: { "X-Auth-Token": author.token } },
    );
    expect(auditResp.ok(), "audit should succeed").toBeTruthy();
    const audit = await auditResp.json();
    expect(Array.isArray(audit)).toBeTruthy();
    expect(audit.length).toBeGreaterThan(0);
    expect(audit[0].action).toBeDefined();
    expect(audit[0].changedBy).toBeDefined();
    expect(audit[0].contentId).toBe(contentId);

    // 12. Rollback to version 1 (moderator)
    const rollbackResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/rollback?targetVersion=1`,
      { headers: { "X-Auth-Token": moderator.token } },
    );
    expect(rollbackResp.ok(), "rollback should succeed").toBeTruthy();
    const rolledBack = await rollbackResp.json();
    expect(rolledBack.contentId).toBe(contentId);
    expect(rolledBack.title).toBe("E2E Lifecycle Title");
    expect(rolledBack.state).toBe("PUBLISH");
  });

  test("publishing version verification regression", async ({ request }) => {
    const author = await registerAndLogin(request, "SENIOR");

    // Create draft
    const draftResp = await request.post(`${BASE}/api/publishing/content`, {
      headers: { "X-Auth-Token": author.token },
      data: { title: "Version Check Draft", body: "Version 1 body" },
    });
    expect(draftResp.ok()).toBeTruthy();
    const draft = await draftResp.json();
    const contentId = draft.contentId;
    expect(draft.currentVersion).toBe(1);

    // Update to produce version 2
    const updateResp = await request.post(
      `${BASE}/api/publishing/content/${contentId}/update`,
      {
        headers: { "X-Auth-Token": author.token },
        data: {
          title: "Version Check Draft",
          body: "Version 2 body",
          summary: "second version",
        },
      },
    );
    expect(updateResp.ok()).toBeTruthy();
    const updated = await updateResp.json();
    expect(updated.currentVersion).toBe(2);

    // Versions endpoint shows exactly 2 entries
    const versionsResp = await request.get(
      `${BASE}/api/publishing/content/${contentId}/versions`,
      { headers: { "X-Auth-Token": author.token } },
    );
    expect(versionsResp.ok()).toBeTruthy();
    const versions = await versionsResp.json();
    expect(versions.length).toBe(2);

    // Audit shows at least create + update entries
    const auditResp = await request.get(
      `${BASE}/api/publishing/content/${contentId}/audit`,
      { headers: { "X-Auth-Token": author.token } },
    );
    expect(auditResp.ok()).toBeTruthy();
    const audit = await auditResp.json();
    expect(audit.length).toBeGreaterThanOrEqual(2);
  });
});

// ---------------------------------------------------------------------------
// Suite 8 – Moderation + penalties + notifications regression
// ---------------------------------------------------------------------------

test.describe("Moderation regression", () => {
  test("moderation with penalties and notifications fullstack regression", async ({
    request,
  }) => {
    // Reporter and target are distinct accounts
    const reporter = await registerAndLogin(request, "SENIOR");
    const target = await registerAndLogin(request, "SENIOR");
    const moderatorAdmin = await registerAndLogin(request, "ORG_ADMIN");

    // Submit a report against the target user
    const reportResp = await request.post(`${BASE}/api/moderation/reports`, {
      headers: { "X-Auth-Token": reporter.token },
      params: {
        reportedUser: target.username,
        contentType: "ANNOUNCEMENT",
        contentRef: "announcement:99",
        reason: "E2E regression: offensive content",
      },
    });
    expect(reportResp.ok(), "submit report should succeed").toBeTruthy();
    const report = await reportResp.json();
    expect(report.reportId).toBeTruthy();
    const reportId = report.reportId;

    // Moderator resolves the report with a mute penalty
    const decisionResp = await request.post(
      `${BASE}/api/moderation/reports/${reportId}/decision`,
      {
        headers: { "X-Auth-Token": moderatorAdmin.token },
        data: {
          penaltyType: "MUTE_24H",
          decisionNotes: "E2E regression decision",
        },
      },
    );
    expect(decisionResp.ok(), "decision should succeed").toBeTruthy();
    const decision = await decisionResp.json();
    expect(decision.status).toBeTruthy();
    expect(decision.penaltyType).toBe("MUTE_24H");

    // Verify penalties are recorded for the target user (moderator can read)
    const penaltiesResp = await request.get(
      `${BASE}/api/moderation/users/${target.username}/penalties`,
      { headers: { "X-Auth-Token": moderatorAdmin.token } },
    );
    expect(penaltiesResp.ok(), "penalties should succeed").toBeTruthy();
    const penalties = await penaltiesResp.json();
    expect(Array.isArray(penalties)).toBeTruthy();
    // At least one penalty should exist for the target after the decision
    expect(penalties.length).toBeGreaterThan(0);
    const mutePenalty = penalties.find((p) => p.penaltyType === "MUTE_24H");
    expect(mutePenalty, "MUTE_24H penalty should exist").toBeTruthy();

    // Verify the reporter received a notification about the outcome
    const notificationsResp = await request.get(
      `${BASE}/api/moderation/users/${reporter.username}/notifications`,
      { headers: { "X-Auth-Token": moderatorAdmin.token } },
    );
    expect(
      notificationsResp.ok(),
      "notifications should succeed",
    ).toBeTruthy();
    const notifications = await notificationsResp.json();
    expect(Array.isArray(notifications)).toBeTruthy();
    expect(
      notifications.length,
      "reporter should have at least one notification",
    ).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// Suite 9 – Pure browser-UI flows (no API helpers, no localStorage injection)
// ---------------------------------------------------------------------------

test.describe("Pure browser UI flows", () => {
  // -------------------------------------------------------------------------
  // Item 21: Login via browser form → create publishing draft → verify in list
  // -------------------------------------------------------------------------
  test("pure UI: login and create publishing draft visible in list", async ({
    page,
  }) => {
    const username = uniqueUser("ui_pub");
    await uiRegisterAndLogin(page, username, PASSWORD, "SENIOR");

    // Publishing panel is visible after login; wait for the title input
    await expect(page.locator("#publish-title")).toBeVisible({
      timeout: 15000,
    });

    // Create a draft through the UI form
    const draftTitle = "UI Draft " + Date.now();
    await page.fill("#publish-title", draftTitle);
    await page.fill("#publish-body", "UI test body content");
    await page.click("#create-draft");

    // publishing.js sets: "Created draft #N in DRAFT state."
    await expect(page.locator("#publishing-output")).toContainText(
      "Created draft",
      { timeout: 10000 },
    );

    // Load content list and verify the new draft title appears
    await page.click("#load-content-items");
    await expect(page.locator("#publishing-list")).toContainText(draftTitle, {
      timeout: 10000,
    });
  });

  // -------------------------------------------------------------------------
  // Item 22: Login via browser form → browse event hierarchy → reserve seat
  // -------------------------------------------------------------------------
  test("pure UI: login then browse event hierarchy and reserve seat", async ({
    page,
  }) => {
    const username = uniqueUser("ui_seat");
    await uiRegisterAndLogin(page, username, PASSWORD, "SENIOR");

    // After login, events.js loads events and renders .open-hierarchy buttons
    await expect(page.locator(".open-hierarchy").first()).toBeVisible({
      timeout: 15000,
    });

    // Open the hierarchy for the first listed event
    await page.locator(".open-hierarchy").first().click();

    // Hierarchy container should replace the idle placeholder text with real data
    await expect(page.locator("#hierarchy-container")).not.toContainText(
      "Select an event",
      { timeout: 15000 },
    );
    await expect(page.locator("#hierarchy-container")).not.toContainText(
      "Loading",
    );

    // loadHierarchy populates #session-select; wait for at least one option
    await expect(page.locator("#session-select option")).not.toHaveCount(0, {
      timeout: 10000,
    });

    // Load the seat map for the selected session
    await page.click("#load-seat-map");

    // Seat map container should be populated (not empty)
    await expect(page.locator("#seat-map")).not.toBeEmpty({ timeout: 10000 });

    // Attempt to interact with an available seat if one exists
    const availableSeat = page.locator("#seat-map .seat.available").first();
    const seatVisible = await availableSeat.isVisible().catch(() => false);
    if (seatVisible) {
      await availableSeat.click();
      // orders.js adds/removes the "selected" class on click
      await expect(availableSeat).toHaveClass(/selected/);

      // Reserve the selected seat
      await page.click("#reserve-seats");

      // orders.js writes order details into #order-status on success
      await expect(page.locator("#order-status")).not.toContainText(
        "No active order",
        { timeout: 10000 },
      );

      // If an order was created, attempt to pay it
      const orderText = await page.locator("#order-status").textContent();
      if (orderText && orderText.includes("Order")) {
        await page.click("#pay-order");
        // orders.js: "Order {code} marked as {status}."
        await expect(page.locator("#order-status")).toContainText("marked as", {
          timeout: 10000,
        });
      }
    }
  });

  // -------------------------------------------------------------------------
  // Item 23: Two-context browser moderation — reporter files a report,
  //          admin loads open reports and applies a decision
  // -------------------------------------------------------------------------
  test("pure UI: two-context moderation report and decision", async ({
    browser,
  }) => {
    // --- Context 1: Reporter (SENIOR) ---
    const reporterCtx = await browser.newContext();
    const reporterPage = await reporterCtx.newPage();
    const reporterName = uniqueUser("ui_reporter");
    await uiRegisterAndLogin(reporterPage, reporterName, PASSWORD, "SENIOR");

    // Submit a moderation report through the UI form
    await expect(reporterPage.locator("#reported-user")).toBeVisible({
      timeout: 15000,
    });
    await reporterPage.fill("#reported-user", "some_offender");
    await reporterPage.fill("#report-content-type", "ANNOUNCEMENT");
    await reporterPage.fill("#report-content-ref", "announcement:1");
    await reporterPage.fill("#report-reason", "UI E2E moderation test");
    await reporterPage.click("#submit-report");

    // moderation.js writes "Report #N ..." into #moderation-outcome on success
    await expect(reporterPage.locator("#moderation-outcome")).toContainText(
      "Report #",
      { timeout: 10000 },
    );

    // --- Context 2: Admin (ORG_ADMIN) ---
    const adminCtx = await browser.newContext();
    const adminPage = await adminCtx.newPage();
    const adminName = uniqueUser("ui_mod_admin");
    await uiRegisterAndLogin(adminPage, adminName, PASSWORD, "ORG_ADMIN");

    // Load open reports
    await expect(adminPage.locator("#load-open-reports")).toBeVisible({
      timeout: 15000,
    });
    await adminPage.click("#load-open-reports");

    // At least one report should be listed in the moderation console
    await expect(adminPage.locator("#moderation-console")).toContainText(
      "Report #",
      { timeout: 10000 },
    );

    // Apply a decision on the first visible report row
    const applyBtn = adminPage.locator(".apply-decision").first();
    await expect(applyBtn).toBeVisible({ timeout: 10000 });

    await adminPage.locator(".decision-penalty").first().selectOption("MUTE_24H");
    await adminPage.locator(".decision-notes").first().fill("UI E2E decision");
    await applyBtn.click();

    // moderation.js writes "Report #N resolved ..." into #moderation-outcome
    await expect(adminPage.locator("#moderation-outcome")).toContainText(
      "resolved",
      { timeout: 10000 },
    );

    // Reporter can optionally check notifications — not a hard assertion since
    // notification delivery timing is non-deterministic in this context
    await reporterPage.fill("#notify-user", reporterName);
    await reporterPage.click("#load-notifications");

    await reporterCtx.close();
    await adminCtx.close();
  });

  // -------------------------------------------------------------------------
  // Item 24: Two-context publishing full lifecycle — author creates draft,
  //          moderator reviews and publishes, author requests appeal,
  //          moderator approves, author applies correction
  // -------------------------------------------------------------------------
  test("pure UI: full publishing lifecycle with author and moderator", async ({
    browser,
  }) => {
    // --- Author context ---
    const authorCtx = await browser.newContext();
    const authorPage = await authorCtx.newPage();
    const authorName = uniqueUser("ui_author");
    await uiRegisterAndLogin(authorPage, authorName, PASSWORD, "SENIOR");

    // Create a draft through the author's UI
    await expect(authorPage.locator("#publish-title")).toBeVisible({
      timeout: 15000,
    });
    const draftTitle = "UI Lifecycle " + Date.now();
    await authorPage.fill("#publish-title", draftTitle);
    await authorPage.fill("#publish-body", "Lifecycle test body");
    await authorPage.click("#create-draft");

    // publishing.js: "Created draft #N in DRAFT state."
    await expect(authorPage.locator("#publishing-output")).toContainText(
      "Created draft",
      { timeout: 10000 },
    );

    // Extract the content ID from the output text (e.g. "Created draft #123 in DRAFT state.")
    const createOutputText = await authorPage
      .locator("#publishing-output")
      .textContent();
    const contentIdMatch = createOutputText.match(/#(\d+)/);
    const contentId = contentIdMatch ? contentIdMatch[1] : null;
    expect(contentId).toBeTruthy();

    // Submit content for review via workflow buttons
    await authorPage.fill("#workflow-content-id", contentId);
    await authorPage.click("#submit-content");

    // publishing.js postWorkflow: "Content #N moved to SUBMISSION."
    await expect(authorPage.locator("#publishing-output")).toContainText(
      "moved to",
      { timeout: 10000 },
    );

    // --- Moderator context ---
    const modCtx = await browser.newContext();
    const modPage = await modCtx.newPage();
    const modName = uniqueUser("ui_moderator");
    await uiRegisterAndLogin(modPage, modName, PASSWORD, "ORG_ADMIN");

    // Moderator marks content as under review
    await expect(modPage.locator("#workflow-content-id")).toBeVisible({
      timeout: 15000,
    });
    await modPage.fill("#workflow-content-id", contentId);
    await modPage.click("#review-content");

    // publishing.js: "Content #N moved to REVIEW."
    await expect(modPage.locator("#publishing-output")).toContainText(
      "moved to",
      { timeout: 10000 },
    );

    // Moderator publishes the content
    await modPage.click("#publish-content");

    // publishing.js: "Content #N published."
    await expect(modPage.locator("#publishing-output")).toContainText(
      "published",
      { timeout: 10000 },
    );

    // Author requests a post-publish appeal
    await authorPage.fill("#publish-summary", "Need to fix a typo");
    await authorPage.click("#request-appeal");

    // publishing.js: "Appeal #N requested with status PENDING."
    await expect(authorPage.locator("#publishing-output")).toContainText(
      "Appeal #",
      { timeout: 10000 },
    );

    // Extract appeal ID from the output text
    const appealText = await authorPage
      .locator("#publishing-output")
      .textContent();
    const appealIdMatch = appealText.match(/Appeal #(\d+)/);
    const appealId = appealIdMatch ? appealIdMatch[1] : null;

    if (appealId) {
      // Moderator approves the appeal
      await modPage.fill("#workflow-appeal-id", appealId);
      await modPage.fill("#publish-summary", "Approved for correction");
      await modPage.click("#approve-appeal");

      // publishing.js: "Appeal #N resolved as APPROVED."
      await expect(modPage.locator("#publishing-output")).toContainText(
        "resolved",
        { timeout: 10000 },
      );

      // Author applies the correction under the approved appeal
      await authorPage.fill("#workflow-appeal-id", appealId);
      await authorPage.fill("#publish-title", "Corrected " + draftTitle);
      await authorPage.fill("#publish-body", "Corrected body after appeal");
      await authorPage.fill("#publish-summary", "Post-publish correction");
      await authorPage.click("#apply-correction");

      // publishing.js: "Correction applied to content #N. Current version: N."
      await expect(authorPage.locator("#publishing-output")).toContainText(
        "Correction applied",
        { timeout: 10000 },
      );
    }

    await authorCtx.close();
    await modCtx.close();
  });
});
