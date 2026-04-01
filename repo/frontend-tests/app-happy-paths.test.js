const path = require("path");

function chainFromDoneFail(onDone, onFail) {
  return {
    done(fn) {
      if (onDone) {
        onDone(fn);
      }
      return this;
    },
    fail(fn) {
      if (onFail) {
        onFail(fn);
      }
      return this;
    },
  };
}

function renderShell() {
  document.body.innerHTML = `
    <button id="auth-logout" class="is-hidden"></button>
    <div id="auth-message"></div>
    <input id="auth-username" />
    <input id="auth-password" />
    <select id="auth-role"><option value="SENIOR">SENIOR</option></select>
    <button id="auth-register"></button>
    <button id="auth-login"></button>
    <div id="hierarchy-container"></div>
    <div id="ticket-options"></div>
    <div id="seat-map"></div>
    <div id="order-status"></div>
    <div id="search-results"></div>
    <div id="page-info"></div>
    <div id="typeahead"></div>
    <div id="moderation-console"></div>
    <div id="moderation-outcome"></div>
    <div id="publishing-list"></div>
    <div id="publishing-output"></div>
    <div id="inventory-alert"></div>
    <ul id="event-list"></ul>
    <select id="session-select"><option value="1">one</option></select>
    <select id="ticket-type-select"><option value="3">three</option></select>
    <select id="channel-select"><option value="ONLINE_PORTAL">ONLINE_PORTAL</option></select>
    <input id="workflow-content-id" value="101" />
    <input id="workflow-appeal-id" />
    <input id="workflow-left-version" />
    <input id="workflow-right-version" />
    <input id="workflow-rollback-version" />
    <button id="load-seat-map"></button>
    <button id="reserve-seats"></button>
    <button id="pay-order"></button>
    <section id="moderation-panel" data-auth-section="authenticated" data-required-roles="ORG_ADMIN,PLATFORM_ADMIN">
      <input id="reported-user" />
      <input id="report-content-type" />
      <input id="report-content-ref" />
      <input id="report-reason" />
      <input id="report-evidence" type="file" />
      <button id="submit-report"></button>
      <button id="load-open-reports"></button>
      <input id="notify-user" />
      <button id="load-notifications"></button>
      <button id="load-penalties"></button>
    </section>
    <section id="publishing-panel" data-auth-section="authenticated">
      <input id="publish-title" />
      <input id="publish-body" />
      <input id="publish-summary" />
      <button id="create-draft"></button>
      <button id="load-content-items"></button>
      <button id="update-draft"></button>
      <button id="submit-content"></button>
      <button id="review-content"></button>
      <button id="publish-content"></button>
      <button id="request-appeal"></button>
      <button id="approve-appeal"></button>
      <button id="apply-correction"></button>
      <button id="rollback-content"></button>
      <button id="show-diff"></button>
      <button id="show-audit"></button>
    </section>
  `;
}

function loadBrowserScripts() {
  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/events.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/orders.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/moderation.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/publishing.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/auth.js"));
}

beforeEach(() => {
  jest.resetModules();
  window.localStorage.clear();
  renderShell();

  const $ = require("jquery");
  global.$ = $;
  global.jQuery = $;

  loadBrowserScripts();
});

test("login flow stores auth and resets leaked UI state", () => {
  window.SilverStage.state.currentEventId = 42;
  window.SilverStage.state.selectedSeatIds = [1001, 1002];
  $("#search-results").html("leaked");
  $("#publishing-list").html("stale");

  const loadEventsSpy = jest.fn();
  window.SilverStage.Events.loadEvents = loadEventsSpy;

  $("#auth-username").val("senior_user");
  $("#auth-password").val("Passw0rd!23");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        token: "token-1",
        username: "senior_user",
        role: "ORG_ADMIN",
        visibleMenus: ["Discovery", "Publishing"],
      });
    }),
  );

  window.SilverStage.Auth.init();
  $("#auth-login").trigger("click");

  expect(window.localStorage.getItem("silverstage.authToken")).toBe("token-1");
  expect(window.SilverStage.state.currentEventId).toBe(null);
  expect(window.SilverStage.state.selectedSeatIds).toEqual([]);
  expect($("#search-results").text()).toBe("Use the search tools above.");
  expect($("#publishing-list").text()).toBe("No content loaded.");
  expect($("#auth-message").text()).toContain("Login successful");
  expect($("#auth-logout").hasClass("is-hidden")).toBe(false);
  expect($("#moderation-panel").length).toBe(1);
  expect(loadEventsSpy).toHaveBeenCalled();
});

test("seat reservation happy path posts selected seats", () => {
  window.localStorage.setItem("silverstage.authToken", "seat-token");
  window.localStorage.setItem("silverstage.authUser", "senior_alpha");
  window.SilverStage.state.currentEventId = 1;
  window.SilverStage.state.selectedSeatIds = [11, 12];

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        zones: [],
        remainingQuota: 25,
        quotaReached: false,
        lowInventory: false,
      });
    }),
  );

  $.ajax = jest.fn((request) => {
    expect(request.url).toBe("/api/seat-orders");
    expect(request.headers["X-Auth-Token"]).toBe("seat-token");
    const payload = JSON.parse(request.data);
    expect(payload.eventId).toBe(1);
    expect(payload.buyerReference).toBe("senior_alpha");
    expect(payload.seatIds).toEqual([11, 12]);
    return chainFromDoneFail((done) => {
      done({
        orderId: 99,
        orderCode: "ORD-ABC",
        holdExpiresAt: "2026-04-01T10:00:00",
        cancelExpiresAt: "2026-04-01T10:30:00",
      });
    });
  });

  window.SilverStage.Orders.init();
  $("#reserve-seats").trigger("click");

  expect(window.SilverStage.state.selectedSeatIds).toEqual([]);
  expect($("#order-status").text()).toContain("Order ORD-ABC created");
  expect($.ajax).toHaveBeenCalledTimes(1);
});

test("moderation happy path submits report without reporterUser field", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#reported-user").val("offending_user");
  $("#report-content-type").val("ANNOUNCEMENT");
  $("#report-content-ref").val("announcement:3");
  $("#report-reason").val("Harassment");

  $.ajax = jest.fn((request) => {
    expect(request.url).toBe("/api/moderation/reports");
    expect(request.method).toBe("POST");
    expect(request.headers["X-Auth-Token"]).toBe("mod-token");
    expect(request.data.get("reporterUser")).toBeNull();
    expect(request.data.get("reportedUser")).toBe("offending_user");
    return chainFromDoneFail((done) => {
      done({ reportId: 501 });
    });
  });

  window.SilverStage.Moderation.init();
  $("#submit-report").trigger("click");

  expect($("#moderation-outcome").text()).toContain("Report #501 submitted");
});

test("publishing happy path creates draft", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#publish-title").val("Community Update");
  $("#publish-body").val("Draft body text");

  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));
  $.ajax = jest.fn((request) => {
    expect(request.url).toBe("/api/publishing/content");
    expect(request.headers["X-Auth-Token"]).toBe("pub-token");
    return chainFromDoneFail((done) => {
      done({ contentId: 77, state: "DRAFT" });
    });
  });

  window.SilverStage.Publishing.init();
  $("#create-draft").trigger("click");

  expect($("#publishing-output").text()).toContain(
    "Created draft #77 in DRAFT state.",
  );
});

test("seat reservation failure path shows out-of-quota alert message", () => {
  window.localStorage.setItem("silverstage.authToken", "seat-token");
  window.localStorage.setItem("silverstage.authUser", "senior_beta");
  window.SilverStage.state.currentEventId = 1;
  window.SilverStage.state.selectedSeatIds = [11];

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        zones: [],
        remainingQuota: 0,
        quotaReached: true,
        lowInventory: false,
      });
    }),
  );

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({
        responseJSON: {
          message: "Quota reached for selected channel. Choose another channel.",
        },
      });
    }),
  );

  window.SilverStage.Orders.init();
  $("#reserve-seats").trigger("click");

  expect($("#order-status").text()).toBe(
    "Quota reached for selected channel. Choose another channel.",
  );
});
