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
  `;
}

beforeEach(() => {
  jest.resetModules();
  window.localStorage.clear();
  renderShell();

  const $ = require("jquery");
  global.$ = $;
  global.jQuery = $;

  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/events.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/orders.js"));
});

// ---------------------------------------------------------------------------
// Seat-map rendering
// ---------------------------------------------------------------------------

test("renderSeatMap displays zone blocks and seat buttons", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        zones: [
          {
            zoneCode: "Z-1",
            zoneName: "Front",
            seats: [
              { seatId: 1, seatNumber: "A-01", status: "AVAILABLE" },
              { seatId: 2, seatNumber: "A-02", status: "RESERVED" },
            ],
          },
        ],
        remainingQuota: 25,
        quotaReached: false,
        lowInventory: false,
      });
    })
  );

  window.SilverStage.Orders.init();
  $("#load-seat-map").trigger("click");

  const html = $("#seat-map").html();
  expect(html).toContain("Z-1");
  expect(html).toContain("A-01");
  expect(html).toContain("A-02");

  const availableBtn = $("#seat-map button[data-seat-id='1']");
  expect(availableBtn.attr("disabled")).toBeUndefined();

  const reservedBtn = $("#seat-map button[data-seat-id='2']");
  expect(reservedBtn.attr("disabled")).toBe("disabled");
});

test("seat selection toggling adds and removes selected class", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        zones: [
          {
            zoneCode: "Z-1",
            zoneName: "Front",
            seats: [
              { seatId: 1, seatNumber: "A-01", status: "AVAILABLE" },
              { seatId: 2, seatNumber: "A-02", status: "RESERVED" },
            ],
          },
        ],
        remainingQuota: 25,
        quotaReached: false,
        lowInventory: false,
      });
    })
  );

  window.SilverStage.Orders.init();
  $("#load-seat-map").trigger("click");

  const seatBtn = $("#seat-map button[data-seat-id='1']");

  // First click – select
  seatBtn.trigger("click");
  expect(seatBtn.hasClass("selected")).toBe(true);
  expect(window.SilverStage.state.selectedSeatIds).toContain(1);

  // Second click – deselect
  seatBtn.trigger("click");
  expect(seatBtn.hasClass("selected")).toBe(false);
  expect(window.SilverStage.state.selectedSeatIds).toEqual([]);
});

// ---------------------------------------------------------------------------
// Inventory-alert variants
// ---------------------------------------------------------------------------

test("quota reached message shows alert-danger", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 0, quotaReached: true, lowInventory: false });
    })
  );

  window.SilverStage.Orders.init();
  $("#load-seat-map").trigger("click");

  expect($("#inventory-alert").text()).toContain("Quota reached");
  expect($("#inventory-alert").hasClass("alert-danger")).toBe(true);
});

test("low inventory warning shows alert-warning", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 3, quotaReached: false, lowInventory: true });
    })
  );

  window.SilverStage.Orders.init();
  $("#load-seat-map").trigger("click");

  expect($("#inventory-alert").text()).toContain("Low inventory");
  expect($("#inventory-alert").hasClass("alert-warning")).toBe(true);
});

test("normal inventory shows remaining count", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 50, quotaReached: false, lowInventory: false });
    })
  );

  window.SilverStage.Orders.init();
  $("#load-seat-map").trigger("click");

  expect($("#inventory-alert").text()).toContain("Remaining channel inventory: 50");
});

// ---------------------------------------------------------------------------
// Reserve seats
// ---------------------------------------------------------------------------

test("reserve seats success clears selection and shows order", () => {
  window.localStorage.setItem("silverstage.authToken", "seat-token");
  window.SilverStage.state.currentEventId = 1;
  window.SilverStage.state.selectedSeatIds = [1];

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 25, quotaReached: false, lowInventory: false });
    })
  );

  $.ajax = jest.fn((opts) => {
    if (opts.url === "/api/seat-orders") {
      return chainFromDoneFail((done) => {
        done({
          orderId: 99,
          orderCode: "ORD-XYZ",
          holdExpiresAt: "2026-04-01T10:00:00",
          cancelExpiresAt: "2026-04-01T10:30:00",
        });
      });
    }
    return chainFromDoneFail(null, (fail) => fail({}));
  });

  window.SilverStage.Orders.init();
  $("#reserve-seats").trigger("click");

  expect(window.SilverStage.state.selectedSeatIds).toEqual([]);
  expect($("#order-status").text()).toContain("Order ORD-XYZ created");
});

test("reserve seats failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "seat-token");
  window.SilverStage.state.currentEventId = 1;
  window.SilverStage.state.selectedSeatIds = [1];

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 25, quotaReached: false, lowInventory: false });
    })
  );

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({ responseJSON: { message: "Conflict: seat already reserved" } });
    })
  );

  window.SilverStage.Orders.init();
  $("#reserve-seats").trigger("click");

  expect($("#order-status").text()).toContain("Conflict: seat already reserved");
});

test("reserve seats without selection shows prompt", () => {
  window.SilverStage.state.currentEventId = 1;
  window.SilverStage.state.selectedSeatIds = [];

  window.SilverStage.Orders.init();
  $("#reserve-seats").trigger("click");

  expect($("#order-status").text()).toContain(
    "Select event, session, ticket option, and seats first"
  );
});

// ---------------------------------------------------------------------------
// Pay order
// ---------------------------------------------------------------------------

test("pay order success shows status", () => {
  window.localStorage.setItem("silverstage.authToken", "seat-token");
  window.SilverStage.state.lastOrderId = 99;

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 25, quotaReached: false, lowInventory: false });
    })
  );

  $.ajax = jest.fn((opts) => {
    if (opts.url === "/api/seat-orders/99/pay") {
      return chainFromDoneFail((done) => {
        done({ orderCode: "ORD-XYZ", status: "PAID" });
      });
    }
    return chainFromDoneFail(null, (fail) => fail({}));
  });

  window.SilverStage.Orders.init();
  $("#pay-order").trigger("click");

  expect($("#order-status").text()).toContain("ORD-XYZ marked as PAID");
});

test("pay order failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "seat-token");
  window.SilverStage.state.lastOrderId = 99;

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ zones: [], remainingQuota: 25, quotaReached: false, lowInventory: false });
    })
  );

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({ responseJSON: { message: "Payment failed" } });
    })
  );

  window.SilverStage.Orders.init();
  $("#pay-order").trigger("click");

  expect($("#order-status").text()).toContain("Payment failed");
});

test("pay order without lastOrderId shows prompt", () => {
  window.SilverStage.state.lastOrderId = null;

  window.SilverStage.Orders.init();
  $("#pay-order").trigger("click");

  expect($("#order-status").text()).toContain("No recent order to pay");
});

// ---------------------------------------------------------------------------
// Seat-map fetch failure
// ---------------------------------------------------------------------------

test("seat map fetch failure shows error", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({ responseJSON: { message: "Session not found" } });
    })
  );

  window.SilverStage.Orders.init();
  $("#load-seat-map").trigger("click");

  expect($("#seat-map").text()).toContain("Session not found");
});

// ---------------------------------------------------------------------------
// buildSeatOrderPayload
// ---------------------------------------------------------------------------

test("buildSeatOrderPayload includes correct fields", () => {
  window.SilverStage.state.currentEventId = 5;
  window.SilverStage.state.selectedSeatIds = [10, 11];

  // Ensure selectors carry the expected values
  $("#session-select").empty().append('<option value="1" selected>one</option>');
  $("#ticket-type-select").empty().append('<option value="3" selected>three</option>');
  $("#channel-select").empty().append('<option value="ONLINE_PORTAL" selected>ONLINE_PORTAL</option>');

  window.localStorage.setItem("silverstage.authUser", "test_buyer");

  const payload = window.SilverStage.Orders.buildSeatOrderPayload();

  expect(payload.eventId).toBe(5);
  expect(payload.seatIds).toEqual([10, 11]);
  expect(payload.buyerReference).toBe("test_buyer");
  expect(payload.channel).toBe("ONLINE_PORTAL");
});
