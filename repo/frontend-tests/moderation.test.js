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
    <input id="workflow-content-id" value="" />
    <input id="workflow-appeal-id" />
    <input id="workflow-left-version" />
    <input id="workflow-right-version" />
    <input id="workflow-rollback-version" />
    <section id="moderation-panel">
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
  require(path.join(process.cwd(), "src/main/resources/static/js/moderation.js"));
});

test("submit report success shows confirmation", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#reported-user").val("offending_user");
  $("#report-content-type").val("ANNOUNCEMENT");
  $("#report-content-ref").val("announcement:3");
  $("#report-reason").val("Spam");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ reportId: 501 });
    })
  );

  window.SilverStage.Moderation.init();
  $("#submit-report").trigger("click");

  expect($("#moderation-outcome").text()).toContain("Report #501 submitted");
});

test("submit report failure shows error message", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#reported-user").val("offending_user");
  $("#report-content-type").val("ANNOUNCEMENT");
  $("#report-content-ref").val("announcement:3");
  $("#report-reason").val("Spam");

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({ responseJSON: { message: "Unable to submit" } });
    })
  );

  window.SilverStage.Moderation.init();
  $("#submit-report").trigger("click");

  expect($("#moderation-outcome").text()).toContain("Unable to submit");
});

test("load open reports success renders report cards", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([
        {
          reportId: 1,
          reporterUser: "user_a",
          reportedUser: "user_b",
          reason: "Spam",
          evidenceFiles: [],
        },
      ]);
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-open-reports").trigger("click");

  const html = $("#moderation-console").html();
  expect(html).toContain("Report #1");
  expect(html).toContain("user_b");
  expect(html).toContain("Spam");
});

test("load open reports shows no reports message", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([]);
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-open-reports").trigger("click");

  expect($("#moderation-console").text()).toContain("No open reports");
});

test("load open reports failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({});
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-open-reports").trigger("click");

  expect($("#moderation-console").text()).toContain("Failed to load");
});

test("load notifications success renders items", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#notify-user").val("user_b");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([
        {
          message: "You were muted",
          type: "PENALTY",
          readFlag: "N",
          createdAt: "2026-04-01",
        },
      ]);
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-notifications").trigger("click");

  expect($("#moderation-outcome").html()).toContain("You were muted");
});

test("load notifications shows no notifications message", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#notify-user").val("user_b");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([]);
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-notifications").trigger("click");

  expect($("#moderation-outcome").text()).toContain("No notifications");
});

test("load notifications failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#notify-user").val("user_b");

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({});
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-notifications").trigger("click");

  expect($("#moderation-outcome").text()).toContain("Unable to load");
});

test("load penalties success renders items", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#notify-user").val("user_b");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([
        {
          username: "user_b",
          penaltyType: "MUTE_24H",
          active: "Y",
          endsAt: "2026-04-02",
        },
      ]);
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-penalties").trigger("click");

  expect($("#moderation-outcome").html()).toContain("MUTE_24H");
});

test("load penalties shows no penalties message", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");
  $("#notify-user").val("user_b");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([]);
    })
  );

  window.SilverStage.Moderation.init();
  $("#load-penalties").trigger("click");

  expect($("#moderation-outcome").text()).toContain("No active penalties");
});

test("apply decision success shows outcome", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");

  // Render a report card with decision controls into #moderation-console
  // (as loadOpenReports would after a successful GET)
  $("#moderation-console").html(
    `<div class="search-item">` +
      `<strong>Report #1</strong>` +
      `<div class="reservation-tools">` +
      `<select class="decision-penalty" data-report-id="1"><option value="MUTE_24H">24-hour mute</option></select>` +
      `<input class="decision-notes" data-report-id="1" type="text" value="" />` +
      `<button class="apply-decision" data-report-id="1" type="button">Apply Decision</button>` +
      `</div>` +
      `</div>`
  );

  // Mock $.ajax: the decision POST succeeds; any subsequent loadOpenReports call
  // (triggered inside the done handler) also succeeds with an empty list.
  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ reportId: 1, penaltyType: "MUTE_24H" });
      });
    }
    // GET call from the internal loadOpenReports refresh
    return chainFromDoneFail((done) => {
      done([]);
    });
  });

  window.SilverStage.Moderation.init();
  $(".apply-decision[data-report-id='1']").trigger("click");

  expect($("#moderation-outcome").text()).toContain("Report #1 resolved");
});

test("apply decision failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "mod-token");

  // Render report card with decision controls
  $("#moderation-console").html(
    `<div class="search-item">` +
      `<strong>Report #1</strong>` +
      `<div class="reservation-tools">` +
      `<select class="decision-penalty" data-report-id="1"><option value="MUTE_24H">24-hour mute</option></select>` +
      `<input class="decision-notes" data-report-id="1" type="text" value="" />` +
      `<button class="apply-decision" data-report-id="1" type="button">Apply Decision</button>` +
      `</div>` +
      `</div>`
  );

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({});
    })
  );

  window.SilverStage.Moderation.init();
  $(".apply-decision[data-report-id='1']").trigger("click");

  expect($("#moderation-outcome").text()).toContain("Failed to apply");
});
