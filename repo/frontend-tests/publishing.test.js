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

beforeEach(() => {
  jest.resetModules();
  window.localStorage.clear();
  renderShell();

  const $ = require("jquery");
  global.$ = $;
  global.jQuery = $;

  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/publishing.js"));
});

// ---------------------------------------------------------------------------
// Create draft
// ---------------------------------------------------------------------------

test("create draft success shows confirmation", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#publish-title").val("Community Update");
  $("#publish-body").val("Draft body text");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 77, state: "DRAFT" });
      });
    }
    // GET refresh called by loadPublishedContent
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#create-draft").trigger("click");

  expect($("#publishing-output").text()).toContain("Created draft #77");
});

test("create draft failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({ responseJSON: { message: "Unable to create draft" } });
    })
  );

  window.SilverStage.Publishing.init();
  $("#create-draft").trigger("click");

  expect($("#publishing-output").text()).toContain("Unable to create draft");
});

// ---------------------------------------------------------------------------
// Load content items
// ---------------------------------------------------------------------------

test("load content items success renders list", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([
        {
          contentId: 77,
          title: "My Draft",
          state: "DRAFT",
          currentVersion: 1,
          publishedAt: null,
        },
      ]);
    })
  );

  window.SilverStage.Publishing.init();
  $("#load-content-items").trigger("click");

  const html = $("#publishing-list").html();
  expect(html).toContain("#77 My Draft");
  expect(html).toContain("State: DRAFT");
});

test("load content items empty shows message", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => done([]))
  );

  window.SilverStage.Publishing.init();
  $("#load-content-items").trigger("click");

  expect($("#publishing-list").text()).toContain("No content items found");
});

test("load content items failure shows error", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => fail({}))
  );

  window.SilverStage.Publishing.init();
  $("#load-content-items").trigger("click");

  expect($("#publishing-list").text()).toContain("Unable to load");
});

// ---------------------------------------------------------------------------
// Update draft
// ---------------------------------------------------------------------------

test("update draft success shows version", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("77");
  $("#publish-title").val("Updated Title");
  $("#publish-body").val("Updated body");
  $("#publish-summary").val("Summary text");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 77, currentVersion: 2 });
      });
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#update-draft").trigger("click");

  expect($("#publishing-output").text()).toContain("Draft #77 updated to version 2");
});

// ---------------------------------------------------------------------------
// Submit content
// ---------------------------------------------------------------------------

test("submit content success shows state transition", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 101, state: "SUBMISSION" });
      });
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#submit-content").trigger("click");

  expect($("#publishing-output").text()).toContain("Content #101 moved to SUBMISSION");
});

// ---------------------------------------------------------------------------
// Review content
// ---------------------------------------------------------------------------

test("review content success shows state transition", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 101, state: "REVIEW" });
      });
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#review-content").trigger("click");

  expect($("#publishing-output").text()).toContain("moved to REVIEW");
});

// ---------------------------------------------------------------------------
// Publish content
// ---------------------------------------------------------------------------

test("publish content success shows published message", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 101, state: "PUBLISH" });
      });
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#publish-content").trigger("click");

  expect($("#publishing-output").text()).toContain("published");
});

// ---------------------------------------------------------------------------
// Request appeal
// ---------------------------------------------------------------------------

test("request appeal success updates appeal id and shows message", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ id: 5, status: "PENDING" });
    })
  );

  window.SilverStage.Publishing.init();
  $("#request-appeal").trigger("click");

  expect($("#publishing-output").text()).toContain("Appeal #5 requested");
  expect($("#workflow-appeal-id").val()).toBe("5");
});

// ---------------------------------------------------------------------------
// Approve appeal
// ---------------------------------------------------------------------------

test("approve appeal success shows resolution message", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");
  $("#workflow-appeal-id").val("5");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ id: 5, status: "APPROVED" });
    })
  );

  window.SilverStage.Publishing.init();
  $("#approve-appeal").trigger("click");

  expect($("#publishing-output").text()).toContain("Appeal #5 resolved as APPROVED");
});

// ---------------------------------------------------------------------------
// Apply correction
// ---------------------------------------------------------------------------

test("apply correction success shows confirmation", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");
  $("#workflow-appeal-id").val("5");
  $("#publish-title").val("Corrected Title");
  $("#publish-body").val("Corrected body");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 101, currentVersion: 3 });
      });
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#apply-correction").trigger("click");

  expect($("#publishing-output").text()).toContain("Correction applied to content #101");
});

// ---------------------------------------------------------------------------
// Show diff
// ---------------------------------------------------------------------------

test("show diff renders side-by-side versions", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");
  $("#workflow-left-version").val("1");
  $("#workflow-right-version").val("2");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        leftVersion: 1,
        rightVersion: 2,
        leftLines: ["old text"],
        rightLines: ["new text"],
      });
    })
  );

  window.SilverStage.Publishing.init();
  $("#show-diff").trigger("click");

  const html = $("#publishing-output").html();
  expect(html).toContain("v1");
  expect(html).toContain("v2");
  expect(html).toContain("old text");
  expect(html).toContain("new text");
});

// ---------------------------------------------------------------------------
// Show audit
// ---------------------------------------------------------------------------

test("show audit renders entries", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => {
      done([
        {
          action: "CREATE_DRAFT",
          detail: "Initial",
          changedBy: "author",
          changedAt: "2026-04-01",
        },
      ]);
    })
  );

  window.SilverStage.Publishing.init();
  $("#show-audit").trigger("click");

  const html = $("#publishing-output").html();
  expect(html).toContain("CREATE_DRAFT");
  expect(html).toContain("author");
});

test("show audit empty shows message", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn(() =>
    chainFromDoneFail((done) => done([]))
  );

  window.SilverStage.Publishing.init();
  $("#show-audit").trigger("click");

  expect($("#publishing-output").text()).toContain("No audit log entries");
});

// ---------------------------------------------------------------------------
// Rollback
// ---------------------------------------------------------------------------

test("rollback success shows confirmation", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");
  $("#workflow-rollback-version").val("1");

  $.ajax = jest.fn((opts) => {
    if (opts.method === "POST") {
      return chainFromDoneFail((done) => {
        done({ contentId: 101, currentVersion: 4 });
      });
    }
    return chainFromDoneFail((done) => done([]));
  });

  window.SilverStage.Publishing.init();
  $("#rollback-content").trigger("click");

  expect($("#publishing-output").text()).toContain("Rollback applied");
});

// ---------------------------------------------------------------------------
// Generic workflow failure
// ---------------------------------------------------------------------------

test("workflow action failure shows error from server", () => {
  window.localStorage.setItem("silverstage.authToken", "pub-token");
  $("#workflow-content-id").val("101");

  $.ajax = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({ responseJSON: { message: "Forbidden" } });
    })
  );

  window.SilverStage.Publishing.init();
  $("#submit-content").trigger("click");

  expect($("#publishing-output").text()).toContain("Forbidden");
});
