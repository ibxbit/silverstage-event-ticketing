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
    <ul id="event-list"></ul>
    <div id="search-results"></div>
    <div id="publishing-list"></div>
    <div id="hierarchy-container"></div>
    <div id="ticket-options"></div>
    <div id="seat-map"></div>
    <div id="order-status"></div>
    <div id="moderation-console"></div>
    <div id="moderation-outcome"></div>
    <div id="publishing-output"></div>
    <div id="inventory-alert"></div>
    <select id="session-select"></select>
    <select id="ticket-type-select"></select>
    <div id="page-info"></div>
    <div id="typeahead"></div>
    <input id="workflow-content-id" />
    <input id="workflow-appeal-id" />
    <input id="workflow-left-version" />
    <input id="workflow-right-version" />
    <input id="workflow-rollback-version" />
    <section data-auth-section="authenticated" data-required-roles="ORG_ADMIN">admin section</section>
  `;
}

function loadScripts() {
  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/events.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/auth.js"));
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

test("validatePasswordLength rejects short passwords", () => {
  const result = window.SilverStage.Auth.validatePasswordLength("short");

  expect(result).toBe(false);
  expect($("#auth-message").text()).toContain("at least 10 characters");
});

test("validatePasswordLength accepts valid passwords", () => {
  const result = window.SilverStage.Auth.validatePasswordLength("Passw0rd!23");

  expect(result).toBe(true);
});

test("registration success shows confirmation message", () => {
  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => done({ username: "new_user", role: "SENIOR" }))
  );

  $("#auth-username").val("new_user");
  $("#auth-password").val("Passw0rd!23");
  $("#auth-role").val("SENIOR");

  window.SilverStage.Auth.init();
  $("#auth-register").trigger("click");

  expect($("#auth-message").text()).toContain("registered successfully");
});

test("registration failure shows error message", () => {
  $.ajax = jest.fn(() =>
    chainFromDoneFail(
      null,
      (fail) => fail({ responseJSON: { message: "Username taken" } })
    )
  );

  $("#auth-username").val("taken_user");
  $("#auth-password").val("Passw0rd!23");

  window.SilverStage.Auth.init();
  $("#auth-register").trigger("click");

  expect($("#auth-message").text()).toContain("Username taken");
});

test("login failure shows error message", () => {
  $.ajax = jest.fn(() =>
    chainFromDoneFail(
      null,
      (fail) => fail({ responseJSON: { message: "Invalid credentials" } })
    )
  );

  $("#auth-username").val("bad_user");
  $("#auth-password").val("Passw0rd!23");

  window.SilverStage.Auth.init();
  $("#auth-login").trigger("click");

  expect($("#auth-message").text()).toContain("Invalid credentials");
});

test("login success with short password is rejected client-side", () => {
  $.ajax = jest.fn();

  $("#auth-username").val("some_user");
  $("#auth-password").val("short");

  window.SilverStage.Auth.init();
  $("#auth-login").trigger("click");

  expect($("#auth-message").text()).toContain("at least 10 characters");
  expect($.ajax).not.toHaveBeenCalled();
});

test("logout clears auth and resets UI", () => {
  window.localStorage.setItem("silverstage.authToken", "tok");
  window.localStorage.setItem("silverstage.authUser", "user1");
  window.localStorage.setItem("silverstage.authRole", "SENIOR");

  // Stub loadEvents so it does not fire real network calls
  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));

  window.SilverStage.Auth.init();
  window.SilverStage.Auth.logout();

  expect(window.localStorage.getItem("silverstage.authToken")).toBeNull();
  expect(window.localStorage.getItem("silverstage.authUser")).toBeNull();
  expect(window.localStorage.getItem("silverstage.authRole")).toBeNull();
  expect($("#auth-message").text()).toContain("logged out");
});

test("register with short password is rejected client-side", () => {
  $.ajax = jest.fn();

  $("#auth-username").val("some_user");
  $("#auth-password").val("abc");

  window.SilverStage.Auth.init();
  $("#auth-register").trigger("click");

  expect($("#auth-message").text()).toContain("at least 10");
  expect($.ajax).not.toHaveBeenCalled();
});
