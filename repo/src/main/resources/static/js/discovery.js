(function (window) {
  const SilverStage = window.SilverStage;

  function renderDiscoveryItems(response) {
    const items = response.items || [];
    SilverStage.state.discoveryTotal = response.total || 0;

    if (!items.length) {
      $("#search-results").html("No results found.");
    } else {
      const html = items
        .map((item) => {
          const meta = [
            item.type,
            item.author ? `Author: ${item.author}` : null,
            item.category ? `Category: ${item.category}` : null,
            item.wordCount !== null && item.wordCount !== undefined
              ? `Words: ${item.wordCount}`
              : null,
            item.popularity !== null && item.popularity !== undefined
              ? `Popularity: ${item.popularity}`
              : null,
          ]
            .filter(Boolean)
            .join(" | ");

          return `<div class="search-item"><strong>${item.highlightedTitle || item.title}</strong><div>${item.highlightedSnippet || item.snippet || ""}</div><div class="search-meta">${meta}</div></div>`;
        })
        .join("");
      $("#search-results").html(html);
    }

    const totalPages = Math.max(
      1,
      Math.ceil(SilverStage.state.discoveryTotal / SilverStage.state.discoverySize),
    );
    $("#page-info").text(
      `Page ${SilverStage.state.discoveryPage + 1} / ${totalPages} (total ${SilverStage.state.discoveryTotal})`,
    );
  }

  function fetchSuggestions(query) {
    if (!query || query.length < 2) {
      $("#typeahead").empty();
      return;
    }
    $.getJSON(`/api/discovery/suggestions?q=${encodeURIComponent(query)}`)
      .done((response) => {
        const suggestions = response.suggestions || [];
        const chips = suggestions
          .map(
            (s) =>
              `<button type="button" class="suggest-chip" data-suggestion="${s}">${s}</button>`,
          )
          .join("");
        $("#typeahead").html(chips);
      })
      .fail(() => {
        $("#typeahead").empty();
      });
  }

  function runSearch(page) {
    SilverStage.state.discoveryMode = "search";
    SilverStage.state.discoveryPage = page;

    const params = new URLSearchParams();
    params.set("q", $("#search-input").val() || "");
    params.set("type", $("#search-type").val() || "ALL");
    params.set("author", $("#search-author").val() || "");
    params.set("category", $("#search-category").val() || "");
    const minWords = $("#search-min-words").val() || "";
    const maxWords = $("#search-max-words").val() || "";
    if (minWords !== "") {
      params.set("minWords", minWords);
    }
    if (maxWords !== "") {
      params.set("maxWords", maxWords);
    }
    params.set("sort", $("#search-sort").val() || "relevance");
    params.set("page", String(SilverStage.state.discoveryPage));
    params.set("size", String(SilverStage.state.discoverySize));

    $.getJSON(`/api/discovery/search?${params.toString()}`)
      .done((response) => {
        renderDiscoveryItems(response);
      })
      .fail(() => {
        $("#search-results").text("Search failed.");
      });
  }

  function browseDiscovery(kind, page) {
    SilverStage.state.discoveryMode = kind;
    SilverStage.state.discoveryPage = page;

    const sort = $("#search-sort").val() || "newest";
    const params = new URLSearchParams();
    params.set("sort", sort);
    params.set("page", String(SilverStage.state.discoveryPage));
    params.set("size", String(SilverStage.state.discoverySize));

    if (kind === "announcements") {
      const author = $("#search-author").val() || "";
      const category = $("#search-category").val() || "";
      const minWords = $("#search-min-words").val() || "";
      const maxWords = $("#search-max-words").val() || "";
      if (author) {
        params.set("author", author);
      }
      if (category) {
        params.set("category", category);
      }
      if (minWords !== "") {
        params.set("minWords", minWords);
      }
      if (maxWords !== "") {
        params.set("maxWords", maxWords);
      }
    }

    $.getJSON(`/api/discovery/browse/${kind}?${params.toString()}`)
      .done((response) => {
        renderDiscoveryItems(response);
      })
      .fail(() => {
        $("#search-results").text("Browse request failed.");
      });
  }

  function init() {
    $("#search-input").on("input", function () {
      fetchSuggestions($(this).val());
    });

    $(document).on("click", ".suggest-chip", function () {
      const suggestion = $(this).data("suggestion");
      $("#search-input").val(suggestion);
      runSearch(0);
    });

    $("#search-btn").on("click", function () {
      runSearch(0);
    });
    $("#browse-seasons").on("click", function () {
      browseDiscovery("seasons", 0);
    });
    $("#browse-sessions").on("click", function () {
      browseDiscovery("sessions", 0);
    });
    $("#browse-announcements").on("click", function () {
      browseDiscovery("announcements", 0);
    });

    $("#prev-page").on("click", function () {
      if (SilverStage.state.discoveryPage === 0) {
        return;
      }
      const nextPage = SilverStage.state.discoveryPage - 1;
      if (SilverStage.state.discoveryMode === "search") {
        runSearch(nextPage);
      } else {
        browseDiscovery(SilverStage.state.discoveryMode, nextPage);
      }
    });

    $("#next-page").on("click", function () {
      const totalPages = Math.ceil(
        SilverStage.state.discoveryTotal / SilverStage.state.discoverySize,
      );
      if (SilverStage.state.discoveryPage + 1 >= totalPages) {
        return;
      }
      const nextPage = SilverStage.state.discoveryPage + 1;
      if (SilverStage.state.discoveryMode === "search") {
        runSearch(nextPage);
      } else {
        browseDiscovery(SilverStage.state.discoveryMode, nextPage);
      }
    });
  }

  SilverStage.Discovery = {
    init,
    runSearch,
    browseDiscovery,
  };
})(window);
