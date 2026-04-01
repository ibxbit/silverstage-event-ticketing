(function (window) {
  const SilverStage = (window.SilverStage = window.SilverStage || {});

  const state = {
    currentEventId: null,
    selectedSeatIds: [],
    lastOrderId: null,
    discoveryMode: "search",
    discoveryPage: 0,
    discoveryTotal: 0,
    discoverySize: 8,
  };

  const defaults = {
    hierarchy: "Select an event to load seasons, sessions, zones, and seats.",
    ticketOptions: "Select an event to compare ticket options.",
    seatMap: "",
    orderStatus: "No active order.",
    searchResults: "Use the search tools above.",
    pageInfo: "Page 1 / 1 (total 0)",
    moderationConsole: "No moderation cases loaded.",
    moderationOutcome: "Moderation outcomes will appear here.",
    publishingList: "No content loaded.",
    publishingOutput: "Workflow output appears here.",
    inventoryAlert: "Select ticket option and channel to see quota alerts.",
  };
  const authSectionRegistry = {};
  let authSectionSeed = 0;

  function computeVisibility(sectionRule, requiredRoles, authenticated, role) {
    let visible = true;
    if (sectionRule === "authenticated") {
      visible = authenticated;
    }
    if (visible && requiredRoles.length) {
      visible = requiredRoles.includes(role);
    }
    return visible;
  }

  function registerAuthSections() {
    $("[data-auth-section]").each(function () {
      const $section = $(this);
      let sectionKey = $section.attr("data-auth-key");
      if (!sectionKey) {
        sectionKey = `auth-section-${authSectionSeed}`;
        authSectionSeed += 1;
        $section.attr("data-auth-key", sectionKey);
      }
      if (authSectionRegistry[sectionKey]) {
        return;
      }

      const sectionRule = $section.attr("data-auth-section");
      const roleRule = ($section.attr("data-required-roles") || "").trim();
      const requiredRoles = roleRule
        ? roleRule.split(",").map((item) => item.trim())
        : [];
      const $placeholder = $(
        `<span data-auth-placeholder="${sectionKey}"></span>`,
      );
      $section.before($placeholder);

      authSectionRegistry[sectionKey] = {
        sectionRule,
        requiredRoles,
        $element: $section,
        $placeholder,
      };
    });
  }

  function setAuthSectionAttached(entry, shouldAttach) {
    const inDom = document.body.contains(entry.$element[0]);
    if (shouldAttach && !inDom) {
      entry.$placeholder.after(entry.$element);
      return;
    }
    if (!shouldAttach && inDom) {
      entry.$element.detach();
    }
  }

  function getAuthToken() {
    return window.localStorage.getItem("silverstage.authToken") || "";
  }

  function getAuthRole() {
    return window.localStorage.getItem("silverstage.authRole") || "";
  }

  function isAuthenticated() {
    return Boolean(getAuthToken());
  }

  function authHeaders(extraHeaders) {
    const headers = Object.assign({}, extraHeaders || {});
    const token = getAuthToken();
    if (token) {
      headers["X-Auth-Token"] = token;
    }
    return headers;
  }

  function resetRuntimeState() {
    state.currentEventId = null;
    state.selectedSeatIds = [];
    state.lastOrderId = null;
    state.discoveryMode = "search";
    state.discoveryPage = 0;
    state.discoveryTotal = 0;
  }

  function resetDynamicDom() {
    $("#publishing-list").html(defaults.publishingList);
    $("#publishing-output").html(defaults.publishingOutput);
    $("#search-results").html(defaults.searchResults);
    $("#typeahead").empty();
    $("#moderation-console").html(defaults.moderationConsole);
    $("#moderation-outcome").html(defaults.moderationOutcome);
    $("#hierarchy-container").html(defaults.hierarchy);
    $("#ticket-options").html(defaults.ticketOptions);
    $("#seat-map").html(defaults.seatMap);
    $("#inventory-alert")
      .text(defaults.inventoryAlert)
      .removeClass("alert-danger alert-warning");
    $("#order-status").html(defaults.orderStatus);
    $("#event-list").empty();
    $("#session-select").empty();
    $("#ticket-type-select").empty();
    $("#page-info").text(defaults.pageInfo);
    $("#workflow-content-id").val("");
    $("#workflow-appeal-id").val("");
    $("#workflow-left-version").val("");
    $("#workflow-right-version").val("");
    $("#workflow-rollback-version").val("");
  }

  function resetForNewSession() {
    resetRuntimeState();
    resetDynamicDom();
  }

  function clearAuthStorage() {
    window.localStorage.clear();
  }

  function setAuthSession(response) {
    window.localStorage.setItem("silverstage.authToken", response.token || "");
    window.localStorage.setItem(
      "silverstage.authUser",
      response.username || "",
    );
    window.localStorage.setItem("silverstage.authRole", response.role || "");
  }

  function applyAuthorizationUi() {
    const authenticated = isAuthenticated();
    const role = getAuthRole();

    registerAuthSections();

    Object.keys(authSectionRegistry).forEach((sectionKey) => {
      const entry = authSectionRegistry[sectionKey];
      const visible = computeVisibility(
        entry.sectionRule,
        entry.requiredRoles,
        authenticated,
        role,
      );
      setAuthSectionAttached(entry, visible);
    });

    $("#auth-logout").toggleClass("is-hidden", !authenticated);
  }

  SilverStage.state = state;
  SilverStage.Core = {
    defaults,
    getAuthToken,
    getAuthRole,
    isAuthenticated,
    authHeaders,
    resetRuntimeState,
    resetDynamicDom,
    resetForNewSession,
    clearAuthStorage,
    setAuthSession,
    applyAuthorizationUi,
  };
})(window);
