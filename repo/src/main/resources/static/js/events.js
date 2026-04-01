(function (window) {
  const SilverStage = window.SilverStage;

  function renderHierarchyTree(hierarchy) {
    const seasons = hierarchy.seasons || [];
    if (seasons.length === 0) {
      return "<p>No seasons configured for this event yet.</p>";
    }

    const seasonBlocks = seasons
      .map((season) => {
        const sessions = season.sessions || [];
        const sessionBlocks = sessions
          .map((session) => {
            const zones = session.zones || [];
            const zoneBlocks = zones
              .map((zone) => {
                const seats = zone.seats || [];
                const seatBlocks = seats
                  .map((seat) => `<li>${seat.seatNumber} (${seat.status})</li>`)
                  .join("");
                return `<li><strong>${zone.code}</strong> - ${zone.name} (cap: ${zone.capacity})<ul>${seatBlocks}</ul></li>`;
              })
              .join("");
            return `<li>${session.title} (${session.startTime})<ul>${zoneBlocks}</ul></li>`;
          })
          .join("");
        return `<li><strong>${season.name}</strong><ul>${sessionBlocks}</ul></li>`;
      })
      .join("");

    return `<ul>${seasonBlocks}</ul>`;
  }

  function populateSessionSelector(hierarchy) {
    const $session = $("#session-select");
    $session.empty();
    const sessions = [];

    (hierarchy.seasons || []).forEach((season) => {
      (season.sessions || []).forEach((session) => {
        sessions.push(session);
      });
    });

    if (!sessions.length) {
      $session.append('<option value="">No sessions</option>');
      return;
    }

    sessions.forEach((session) => {
      $session.append(
        `<option value="${session.id}">${session.title} (${session.startTime})</option>`,
      );
    });
  }

  function renderTicketOptions(ticketTypes) {
    if (!ticketTypes.length) {
      $("#ticket-options").html("No ticket options configured for this event.");
      return;
    }

    const rows = ticketTypes
      .map((type) => {
        const tier = (type.tierRules || [])
          .map((rule) => `min ${rule.minQuantity}: $${rule.price}`)
          .join(", ");
        return `<li><strong>${type.name}</strong> (${type.code}) | ${type.visibilityScope} | ${type.saleWindowLabel}<br/>Tiers: ${tier}</li>`;
      })
      .join("");
    $("#ticket-options").html(`<ul>${rows}</ul>`);
  }

  function loadTicketTypes(eventId) {
    const $ticketType = $("#ticket-type-select");
    $ticketType.empty().append('<option value="">Loading...</option>');

    $.getJSON(`/api/events/${eventId}/ticket-types`)
      .done((ticketTypes) => {
        $ticketType.empty();
        if (!ticketTypes.length) {
          $ticketType.append('<option value="">No ticket types</option>');
        } else {
          ticketTypes.forEach((type) => {
            $ticketType.append(
              `<option value="${type.id}">${type.name} | ${type.visibilityScope}</option>`,
            );
          });
        }
        renderTicketOptions(ticketTypes);
      })
      .fail(() => {
        $ticketType.empty().append('<option value="">Error</option>');
        $("#ticket-options").text("Unable to load ticket options.");
      });
  }

  function loadHierarchy(eventId) {
    SilverStage.state.currentEventId = eventId;
    $("#hierarchy-container").html("Loading hierarchy...");
    $.getJSON(`/api/events/${eventId}/hierarchy`)
      .done((response) => {
        $("#hierarchy-container").html(renderHierarchyTree(response));
        populateSessionSelector(response);
        loadTicketTypes(eventId);
      })
      .fail(() => {
        $("#hierarchy-container").text("Failed to load hierarchy from local API.");
      });
  }

  function loadEvents() {
    const $list = $("#event-list");
    $list.empty().append("<li>Loading local events...</li>");

    $.getJSON("/api/events")
      .done((events) => {
        $list.empty();
        if (!events.length) {
          $list.append("<li>No events found in local database.</li>");
          return;
        }

        events.forEach((event) => {
          const item = `<li class="event-card"><div><strong>${event.name}</strong><span>${event.code} | ${event.startDate} to ${event.endDate}</span></div><button type="button" class="open-hierarchy" data-event-id="${event.id}">Open hierarchy</button></li>`;
          $list.append(item);
        });
      })
      .fail(() => {
        $list.empty().append("<li>Unable to read local events API.</li>");
      });
  }

  function init() {
    $(document).on("click", ".open-hierarchy", function () {
      const eventId = $(this).data("event-id");
      SilverStage.state.selectedSeatIds = [];
      loadHierarchy(eventId);
    });

    $("#refresh-events").on("click", function () {
      loadEvents();
    });
  }

  SilverStage.Events = {
    init,
    loadEvents,
    loadHierarchy,
  };
})(window);
