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
    <ul id="event-list"></ul>
    <div id="hierarchy-container"></div>
    <div id="ticket-options"></div>
    <select id="session-select"></select>
    <select id="ticket-type-select"></select>
    <select id="channel-select"><option value="ONLINE_PORTAL">ONLINE_PORTAL</option></select>
    <button id="refresh-events"></button>
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
    <input id="workflow-content-id" />
    <input id="workflow-appeal-id" />
    <input id="workflow-left-version" />
    <input id="workflow-right-version" />
    <input id="workflow-rollback-version" />
    <button id="auth-logout" class="is-hidden"></button>
  `;
}

function loadScripts() {
  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/events.js"));
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

const hierarchyPayload = {
  seasons: [
    {
      name: "Spring",
      sessions: [
        {
          id: 10,
          title: "Matinee",
          startTime: "2026-04-01T14:00",
          stands: [
            {
              code: "ST-A",
              name: "North",
              zones: [
                {
                  code: "Z-1",
                  name: "Zone One",
                  capacity: 20,
                  seats: [{ seatNumber: "A-01", status: "AVAILABLE" }],
                },
              ],
            },
          ],
        },
      ],
    },
  ],
};

function makeHierarchyMock(ticketTypes) {
  return jest.fn((url) => {
    if (url.includes("/hierarchy")) {
      return chainFromDoneFail((done) => done(hierarchyPayload));
    }
    if (url.includes("/ticket-types")) {
      return chainFromDoneFail((done) => done(ticketTypes || []));
    }
    return chainFromDoneFail(null, (fail) => fail({}));
  });
}

test("loadEvents renders event cards on success", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) =>
      done([
        {
          id: 1,
          name: "Arts Festival",
          code: "ARTS-26",
          startDate: "2026-04-01",
          endDate: "2026-06-30",
        },
      ])
    )
  );

  window.SilverStage.Events.loadEvents();

  const html = $("#event-list").html();
  expect(html).toContain("Arts Festival");
  expect(html).toContain("ARTS-26");
  expect($("#event-list").find("button.open-hierarchy").length).toBeGreaterThan(0);
});

test("loadEvents shows message when no events", () => {
  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));

  window.SilverStage.Events.loadEvents();

  expect($("#event-list").text()).toContain("No events found");
});

test("loadEvents shows error on failure", () => {
  $.getJSON = jest.fn(() => chainFromDoneFail(null, (fail) => fail({})));

  window.SilverStage.Events.loadEvents();

  expect($("#event-list").text()).toContain("Unable to read");
});

test("loadHierarchy renders hierarchy tree", () => {
  $.getJSON = makeHierarchyMock([]);

  window.SilverStage.Events.loadHierarchy(1);

  const html = $("#hierarchy-container").html();
  expect(html).toContain("Spring");
  expect(html).toContain("ST-A");
  expect(html).toContain("Z-1");
  expect(html).toContain("A-01 (AVAILABLE)");
});

test("loadHierarchy populates session selector", () => {
  $.getJSON = makeHierarchyMock([]);

  window.SilverStage.Events.loadHierarchy(1);

  const $option = $("#session-select option[value='10']");
  expect($option.length).toBe(1);
  expect($option.text()).toContain("Matinee");
});

test("loadHierarchy shows failure message on error", () => {
  $.getJSON = jest.fn((url) => {
    if (url.includes("/hierarchy")) {
      return chainFromDoneFail(null, (fail) => fail({}));
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Events.loadHierarchy(1);

  expect($("#hierarchy-container").text()).toContain("Failed to load hierarchy");
});

test("loadTicketTypes renders ticket options", () => {
  const ticketTypes = [
    {
      id: 1,
      name: "General",
      code: "GEN",
      visibilityScope: "PUBLIC",
      saleWindowLabel: "Apr-Jun",
      tierRules: [{ minQuantity: 1, price: 25.0 }],
    },
  ];

  $.getJSON = makeHierarchyMock(ticketTypes);

  window.SilverStage.Events.loadHierarchy(1);

  expect($("#ticket-options").html()).toContain("General");
  const $option = $("#ticket-type-select option[value='1']");
  expect($option.length).toBe(1);
});

test("open-hierarchy button click triggers loadHierarchy", () => {
  $.getJSON = jest.fn(() => chainFromDoneFail((done) => done([])));

  // Inject an event card with the open-hierarchy button after init so the
  // delegated event listener (attached to document) picks it up.
  window.SilverStage.Events.init();

  $("#event-list").append(
    `<li class="event-card"><button type="button" class="open-hierarchy" data-event-id="1">Open hierarchy</button></li>`
  );

  // Reset the mock after loading init so we can assert the call made by the click
  $.getJSON = jest.fn((url) => {
    if (url.includes("/hierarchy")) {
      return chainFromDoneFail((done) => done({ seasons: [] }));
    }
    if (url.includes("/ticket-types")) {
      return chainFromDoneFail((done) => done([]));
    }
    return chainFromDoneFail(null, (fail) => fail({}));
  });

  $(".open-hierarchy").trigger("click");

  const calledUrls = $.getJSON.mock.calls.map((call) => call[0]);
  expect(calledUrls.some((url) => url.includes("/api/events/1/hierarchy"))).toBe(true);
});
