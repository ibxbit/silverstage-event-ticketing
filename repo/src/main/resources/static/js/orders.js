(function (window) {
  const SilverStage = window.SilverStage;

  function renderSeatMap(response) {
    const zoneBlocks = (response.zones || [])
      .map((zone) => {
        const seats = (zone.seats || [])
          .map((seat) => {
            const disabled = seat.status !== "AVAILABLE" ? "disabled" : "";
            const selected = SilverStage.state.selectedSeatIds.includes(
              seat.seatId,
            )
              ? "selected"
              : "";
            const classes = `seat ${seat.status.toLowerCase()} ${selected}`;
            return `<button type="button" class="${classes}" data-seat-id="${seat.seatId}" ${disabled}>${seat.seatNumber}</button>`;
          })
          .join("");
        return `<div class="zone-block"><h4>${zone.zoneCode} - ${zone.zoneName}</h4><div class="seat-grid">${seats}</div></div>`;
      })
      .join("");

    $("#seat-map").html(zoneBlocks || "No seats configured for this session.");

    if (response.quotaReached) {
      $("#inventory-alert")
        .text("Quota reached for selected channel. Choose another channel.")
        .addClass("alert-danger");
    } else if (response.lowInventory) {
      $("#inventory-alert")
        .text(
          `Low inventory warning: only ${response.remainingQuota} seats left in this channel.`,
        )
        .addClass("alert-warning");
    } else if (
      response.remainingQuota !== null &&
      response.remainingQuota !== undefined
    ) {
      $("#inventory-alert")
        .text(`Remaining channel inventory: ${response.remainingQuota}`)
        .removeClass("alert-danger alert-warning");
    } else {
      $("#inventory-alert")
        .text(SilverStage.Core.defaults.inventoryAlert)
        .removeClass("alert-danger alert-warning");
    }
  }

  function loadSeatMap() {
    const sessionId = $("#session-select").val();
    const ticketTypeId = $("#ticket-type-select").val();
    const channel = $("#channel-select").val();

    if (!sessionId) {
      $("#seat-map").text("Please select a session first.");
      return;
    }

    let query = "";
    if (ticketTypeId) {
      query = `?ticketTypeId=${ticketTypeId}&channel=${channel}`;
    }

    $.getJSON(`/api/sessions/${sessionId}/seat-map${query}`)
      .done((response) => {
        renderSeatMap(response);
      })
      .fail((xhr) => {
        const msg =
          xhr.responseJSON && xhr.responseJSON.message
            ? xhr.responseJSON.message
            : "Failed to load seat map.";
        $("#seat-map").text(msg);
      });
  }

  function buildSeatOrderPayload() {
    const sessionId = $("#session-select").val();
    const ticketTypeId = $("#ticket-type-select").val();
    const channel = $("#channel-select").val();
    return {
      eventId: Number(SilverStage.state.currentEventId),
      sessionId: Number(sessionId),
      ticketTypeId: Number(ticketTypeId),
      orderCode: `ORD-${Date.now()}`,
      buyerReference:
        window.localStorage.getItem("silverstage.authUser") || "anonymous",
      channel,
      seatIds: SilverStage.state.selectedSeatIds.slice(),
    };
  }

  function init() {
    $(document).on("click", ".seat.available", function () {
      const seatId = Number($(this).data("seat-id"));
      if (SilverStage.state.selectedSeatIds.includes(seatId)) {
        SilverStage.state.selectedSeatIds = SilverStage.state.selectedSeatIds.filter(
          (id) => id !== seatId,
        );
        $(this).removeClass("selected");
      } else {
        SilverStage.state.selectedSeatIds.push(seatId);
        $(this).addClass("selected");
      }
    });

    $("#load-seat-map").on("click", function () {
      SilverStage.state.selectedSeatIds = [];
      loadSeatMap();
    });

    $("#reserve-seats").on("click", function () {
      const sessionId = $("#session-select").val();
      const ticketTypeId = $("#ticket-type-select").val();

      if (
        !SilverStage.state.currentEventId ||
        !sessionId ||
        !ticketTypeId ||
        !SilverStage.state.selectedSeatIds.length
      ) {
        $("#order-status").text(
          "Select event, session, ticket option, and seats first.",
        );
        return;
      }

      $.ajax({
        url: "/api/seat-orders",
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify(buildSeatOrderPayload()),
      })
        .done((response) => {
          SilverStage.state.lastOrderId = response.orderId;
          SilverStage.state.selectedSeatIds = [];
          $("#order-status").html(
            `Order ${response.orderCode} created. Hold until ${response.holdExpiresAt}. Unpaid auto-cancel at ${response.cancelExpiresAt}.`,
          );
          loadSeatMap();
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Seat reservation failed.";
          $("#order-status").text(msg);
          loadSeatMap();
        });
    });

    $("#pay-order").on("click", function () {
      if (!SilverStage.state.lastOrderId) {
        $("#order-status").text("No recent order to pay.");
        return;
      }

      $.ajax({
        url: `/api/seat-orders/${SilverStage.state.lastOrderId}/pay`,
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
      })
        .done((response) => {
          $("#order-status").text(
            `Order ${response.orderCode} marked as ${response.status}.`,
          );
          loadSeatMap();
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Payment action failed.";
          $("#order-status").text(msg);
          loadSeatMap();
        });
    });
  }

  SilverStage.Orders = {
    init,
    loadSeatMap,
    buildSeatOrderPayload,
  };
})(window);
