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

function renderAppShell() {
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
    <input id="workflow-content-id" value="" />
    <input id="workflow-appeal-id" />
    <input id="workflow-left-version" />
    <input id="workflow-right-version" />
    <input id="workflow-rollback-version" />
    <input id="search-input" />
    <select id="search-type"><option value="ALL">ALL</option></select>
    <input id="search-author" />
    <input id="search-category" />
    <input id="search-min-words" />
    <input id="search-max-words" />
    <select id="search-sort"><option value="relevance">relevance</option></select>
    <button id="search-btn"></button>
    <button id="browse-seasons"></button>
    <button id="browse-sessions"></button>
    <button id="browse-announcements"></button>
    <button id="prev-page"></button>
    <button id="next-page"></button>
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

function loadAllModules() {
  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/events.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/orders.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/discovery.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/moderation.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/publishing.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/auth.js"));
}

beforeEach(() => {
  jest.resetModules();
  window.localStorage.clear();
  renderAppShell();

  const $ = require("jquery");
  global.$ = $;
  global.jQuery = $;

  loadAllModules();
});

test("app.js initializes all modules", () => {
  // Spy on each module's init before loading app.js
  const eventsSpy = jest.fn();
  const ordersSpy = jest.fn();
  const discoverySpy = jest.fn();
  const moderationSpy = jest.fn();
  const publishingSpy = jest.fn();
  const authSpy = jest.fn();

  window.SilverStage.Events.init = eventsSpy;
  window.SilverStage.Orders.init = ordersSpy;
  window.SilverStage.Discovery.init = discoverySpy;
  window.SilverStage.Moderation.init = moderationSpy;
  window.SilverStage.Publishing.init = publishingSpy;
  window.SilverStage.Auth.init = authSpy;

  // Stub network calls triggered by Events.loadEvents
  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));

  // Loading app.js triggers jQuery ready which fires immediately in jsdom
  require(path.join(process.cwd(), "src/main/resources/static/app.js"));

  expect(eventsSpy).toHaveBeenCalledTimes(1);
  expect(ordersSpy).toHaveBeenCalledTimes(1);
  expect(discoverySpy).toHaveBeenCalledTimes(1);
  expect(moderationSpy).toHaveBeenCalledTimes(1);
  expect(publishingSpy).toHaveBeenCalledTimes(1);
  expect(authSpy).toHaveBeenCalledTimes(1);
});

test("app.js calls resetDynamicDom", () => {
  const resetSpy = jest.fn();
  window.SilverStage.Core.resetDynamicDom = resetSpy;

  // Stub network calls
  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));

  require(path.join(process.cwd(), "src/main/resources/static/app.js"));

  expect(resetSpy).toHaveBeenCalledTimes(1);
});

test("app.js calls applyAuthorizationUi", () => {
  const authUiSpy = jest.fn();
  window.SilverStage.Core.applyAuthorizationUi = authUiSpy;

  // Stub network calls
  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));

  require(path.join(process.cwd(), "src/main/resources/static/app.js"));

  expect(authUiSpy).toHaveBeenCalledTimes(1);
});

test("app.js calls Events.loadEvents", () => {
  const loadEventsSpy = jest.fn();
  window.SilverStage.Events.loadEvents = loadEventsSpy;

  require(path.join(process.cwd(), "src/main/resources/static/app.js"));

  expect(loadEventsSpy).toHaveBeenCalledTimes(1);
});
