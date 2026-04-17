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

function renderDiscoveryShell() {
  document.body.innerHTML = `
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
    <div id="search-results"></div>
    <div id="page-info"></div>
    <div id="typeahead"></div>
  `;
}

function loadDiscoveryScripts() {
  require(path.join(process.cwd(), "src/main/resources/static/js/core.js"));
  require(path.join(process.cwd(), "src/main/resources/static/js/discovery.js"));
}

beforeEach(() => {
  jest.resetModules();
  window.localStorage.clear();
  renderDiscoveryShell();

  const $ = require("jquery");
  global.$ = $;
  global.jQuery = $;

  loadDiscoveryScripts();
});

test("discovery search renders items", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({
        items: [
          {
            title: "Spring Choir Concert",
            type: "SESSION",
            author: "admin",
            category: "Music",
            wordCount: 120,
            popularity: 42,
            highlightedTitle: "<em>Spring</em> Choir Concert",
            highlightedSnippet: "A wonderful choral event",
          },
        ],
        total: 1,
      });
    })
  );

  window.SilverStage.Discovery.init();
  $("#search-input").val("choir");
  window.SilverStage.Discovery.runSearch(0);

  const html = $("#search-results").html();
  expect(html).toContain("Spring");
  expect(html).toContain("search-item");
  expect(html).toContain("Choir Concert");
});

test("discovery browse seasons calls correct endpoint", () => {
  let capturedUrl = null;
  $.getJSON = jest.fn((url) => {
    capturedUrl = url;
    return chainFromDoneFail((done) => {
      done({ items: [], total: 0 });
    });
  });

  window.SilverStage.Discovery.init();
  $("#browse-seasons").trigger("click");

  expect(capturedUrl).toBeTruthy();
  expect(capturedUrl).toContain("/api/discovery/browse/seasons");
});

test("discovery browse sessions calls correct endpoint", () => {
  let capturedUrl = null;
  $.getJSON = jest.fn((url) => {
    capturedUrl = url;
    return chainFromDoneFail((done) => {
      done({ items: [], total: 0 });
    });
  });

  window.SilverStage.Discovery.init();
  $("#browse-sessions").trigger("click");

  expect(capturedUrl).toBeTruthy();
  expect(capturedUrl).toContain("/api/discovery/browse/sessions");
});

test("discovery browse announcements calls correct endpoint", () => {
  let capturedUrl = null;
  $.getJSON = jest.fn((url) => {
    capturedUrl = url;
    return chainFromDoneFail((done) => {
      done({ items: [], total: 0 });
    });
  });

  window.SilverStage.Discovery.init();
  $("#browse-announcements").trigger("click");

  expect(capturedUrl).toBeTruthy();
  expect(capturedUrl).toContain("/api/discovery/browse/announcements");
});

test("discovery typeahead suggestions render chips", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ suggestions: ["choir concert", "choir rehearsal", "choir festival"] });
    })
  );

  window.SilverStage.Discovery.init();
  $("#search-input").val("choir").trigger("input");

  const html = $("#typeahead").html();
  expect(html).toContain("suggest-chip");
  expect(html).toContain("choir concert");
  expect(html).toContain("choir rehearsal");
});

test("discovery suggestion chip click triggers search", () => {
  let searchCalled = false;
  let searchUrl = null;

  $.getJSON = jest.fn((url) => {
    searchUrl = url;
    searchCalled = true;
    return chainFromDoneFail((done) => {
      done({ items: [], total: 0 });
    });
  });

  window.SilverStage.Discovery.init();

  // Manually inject a chip into the typeahead as if suggestions had loaded
  $("#typeahead").html(
    '<button type="button" class="suggest-chip" data-suggestion="choir annual">choir annual</button>'
  );

  $(".suggest-chip").trigger("click");

  expect($("#search-input").val()).toBe("choir annual");
  expect(searchCalled).toBe(true);
  expect(searchUrl).toContain("/api/discovery/search");
});

test("discovery search failure shows error", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail(null, (fail) => {
      fail({});
    })
  );

  window.SilverStage.Discovery.init();
  window.SilverStage.Discovery.runSearch(0);

  expect($("#search-results").text()).toBe("Search failed.");
});

test("discovery pagination updates page", () => {
  // Set initial state: total 20 items, page size 8 → 3 pages, currently on page 0
  window.SilverStage.state.discoveryTotal = 20;
  window.SilverStage.state.discoverySize = 8;
  window.SilverStage.state.discoveryPage = 0;
  window.SilverStage.state.discoveryMode = "search";

  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ items: [], total: 20 });
    })
  );

  window.SilverStage.Discovery.init();
  $("#next-page").trigger("click");

  expect(window.SilverStage.state.discoveryPage).toBe(1);
});

test("discovery no results shows message", () => {
  $.getJSON = jest.fn(() =>
    chainFromDoneFail((done) => {
      done({ items: [], total: 0 });
    })
  );

  window.SilverStage.Discovery.init();
  window.SilverStage.Discovery.runSearch(0);

  expect($("#search-results").html()).toBe("No results found.");
});
