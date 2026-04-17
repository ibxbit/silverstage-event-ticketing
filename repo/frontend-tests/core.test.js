const path = require("path");

function renderShell() {
  document.body.innerHTML = `
    <div id="search-results"></div>
    <div id="page-info"></div>
    <div id="typeahead"></div>
    <div id="moderation-console"></div>
    <div id="moderation-outcome"></div>
    <div id="hierarchy-container"></div>
    <div id="ticket-options"></div>
    <div id="seat-map"></div>
    <div id="inventory-alert"></div>
    <div id="order-status"></div>
    <ul id="event-list"></ul>
    <select id="session-select"><option value="1">one</option></select>
    <select id="ticket-type-select"><option value="3">three</option></select>
    <div id="publishing-list"></div>
    <div id="publishing-output"></div>
    <input id="workflow-content-id" value="" />
    <input id="workflow-appeal-id" />
    <input id="workflow-left-version" />
    <input id="workflow-right-version" />
    <input id="workflow-rollback-version" />
    <button id="auth-logout" class="is-hidden"></button>
    <section data-auth-section="authenticated" data-required-roles="ORG_ADMIN">admin section</section>
  `;
}

function loadScripts() {
  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
}

beforeEach(() => {
  jest.resetModules();
  window.localStorage.clear();
  renderShell();

  const $ = require("jquery");
  global.$ = $;
  global.jQuery = $;

  loadScripts();
});

test("resetRuntimeState clears runtime fields", () => {
  window.SilverStage.state.currentEventId = 5;
  window.SilverStage.state.selectedSeatIds = [1, 2];
  window.SilverStage.state.lastOrderId = 99;

  window.SilverStage.Core.resetRuntimeState();

  expect(window.SilverStage.state.currentEventId).toBe(null);
  expect(window.SilverStage.state.selectedSeatIds).toEqual([]);
  expect(window.SilverStage.state.lastOrderId).toBe(null);
});

test("resetDynamicDom restores default text", () => {
  $("#search-results").html("stale data");

  window.SilverStage.Core.resetDynamicDom();

  expect($("#search-results").text()).toBe("Use the search tools above.");
});

test("resetDynamicDom clears selectors and inputs", () => {
  // session-select already has an option from renderShell; workflow-content-id gets a value
  $("#workflow-content-id").val("some-value");

  window.SilverStage.Core.resetDynamicDom();

  expect($("#session-select option").length).toBe(0);
  expect($("#workflow-content-id").val()).toBe("");
});

test("setAuthSession stores token and role in localStorage", () => {
  window.SilverStage.Core.setAuthSession({
    token: "t1",
    username: "user1",
    role: "SENIOR",
  });

  expect(window.localStorage.getItem("silverstage.authToken")).toBe("t1");
  expect(window.localStorage.getItem("silverstage.authUser")).toBe("user1");
  expect(window.localStorage.getItem("silverstage.authRole")).toBe("SENIOR");
});

test("clearAuthStorage removes all items", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");
  window.localStorage.setItem("silverstage.authUser", "user1");
  window.localStorage.setItem("silverstage.authRole", "SENIOR");

  window.SilverStage.Core.clearAuthStorage();

  expect(window.localStorage.getItem("silverstage.authToken")).toBeNull();
  expect(window.localStorage.getItem("silverstage.authUser")).toBeNull();
  expect(window.localStorage.getItem("silverstage.authRole")).toBeNull();
});

test("getAuthToken returns stored token", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");

  expect(window.SilverStage.Core.getAuthToken()).toBe("tok");
});

test("isAuthenticated returns true when token present", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");
  expect(window.SilverStage.Core.isAuthenticated()).toBe(true);

  window.localStorage.clear();
  expect(window.SilverStage.Core.isAuthenticated()).toBe(false);
});

test("authHeaders includes X-Auth-Token when authenticated", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");

  const headers = window.SilverStage.Core.authHeaders();

  expect(headers["X-Auth-Token"]).toBe("tok");
});

test("applyAuthorizationUi hides auth sections when not authenticated", () => {
  // No token set — unauthenticated state
  window.SilverStage.Core.applyAuthorizationUi();

  // The section should have been detached from the DOM
  const $section = $("[data-auth-section='authenticated']");
  expect(document.body.contains($section[0])).toBe(false);
});

test("applyAuthorizationUi shows auth sections when authenticated", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");
  window.localStorage.setItem("silverstage.authRole", "ORG_ADMIN");

  window.SilverStage.Core.applyAuthorizationUi();

  const $section = $("[data-auth-section='authenticated']");
  expect(document.body.contains($section[0])).toBe(true);
});

test("applyAuthorizationUi respects required roles", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");
  window.localStorage.setItem("silverstage.authRole", "SENIOR");

  // SENIOR does not satisfy ORG_ADMIN requirement — section should be detached
  window.SilverStage.Core.applyAuthorizationUi();

  const $section = $("[data-auth-section='authenticated'][data-required-roles='ORG_ADMIN']");
  expect(document.body.contains($section[0])).toBe(false);

  // Now elevate to ORG_ADMIN — section should be re-attached
  window.localStorage.setItem("silverstage.authRole", "ORG_ADMIN");
  window.SilverStage.Core.applyAuthorizationUi();

  expect(document.body.contains($section[0])).toBe(true);
});

test("resetForNewSession combines state and DOM reset", () => {
  window.SilverStage.state.currentEventId = 7;
  window.SilverStage.state.selectedSeatIds = [10, 11];
  window.SilverStage.state.lastOrderId = 42;
  $("#search-results").html("dirty content");
  $("#session-select").append('<option value="99">extra</option>');
  $("#workflow-content-id").val("xyz");

  window.SilverStage.Core.resetForNewSession();

  // State reset
  expect(window.SilverStage.state.currentEventId).toBe(null);
  expect(window.SilverStage.state.selectedSeatIds).toEqual([]);
  expect(window.SilverStage.state.lastOrderId).toBe(null);

  // DOM reset
  expect($("#search-results").text()).toBe("Use the search tools above.");
  expect($("#session-select option").length).toBe(0);
  expect($("#workflow-content-id").val()).toBe("");
});
