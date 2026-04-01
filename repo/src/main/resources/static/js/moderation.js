(function (window) {
  const SilverStage = window.SilverStage;

  function renderOpenReports(reports) {
    if (!reports.length) {
      $("#moderation-console").html("No open reports.");
      return;
    }

    const html = reports
      .map((report) => {
        const evidence = (report.evidenceFiles || []).join(", ") || "No evidence";
        return `<div class="search-item"><strong>Report #${report.reportId}</strong><div>Reporter: ${report.reporterUser} | Reported: ${report.reportedUser}</div><div>Reason: ${report.reason}</div><div class="search-meta">Evidence: ${evidence}</div><div class="reservation-tools"><select class="decision-penalty" data-report-id="${report.reportId}"><option value="MUTE_24H">24-hour mute</option><option value="POST_RESTRICT_7D">7-day posting restriction</option><option value="PERMANENT_BAN">Permanent ban</option><option value="NO_ACTION">No action</option></select><input class="decision-notes" data-report-id="${report.reportId}" type="text" placeholder="Decision notes" /><button class="apply-decision" data-report-id="${report.reportId}" type="button">Apply Decision</button></div></div>`;
      })
      .join("");

    $("#moderation-console").html(html);
  }

  function loadOpenReports() {
    $.ajax({
      url: "/api/moderation/reports",
      method: "GET",
      headers: SilverStage.Core.authHeaders(),
    })
      .done((reports) => {
        renderOpenReports(reports || []);
      })
      .fail((xhr) => {
        const msg =
          xhr.responseJSON && xhr.responseJSON.message
            ? xhr.responseJSON.message
            : "Failed to load moderation reports.";
        $("#moderation-console").text(msg);
      });
  }

  function loadNotifications(username) {
    $.ajax({
      url: `/api/moderation/users/${encodeURIComponent(username)}/notifications`,
      method: "GET",
      headers: SilverStage.Core.authHeaders(),
    })
      .done((items) => {
        if (!items.length) {
          $("#moderation-outcome").html("No notifications.");
          return;
        }
        const html = items
          .map(
            (n) =>
              `<div class="search-item"><div>${n.message}</div><div class="search-meta">${n.type} | ${n.readFlag} | ${n.createdAt}</div></div>`,
          )
          .join("");
        $("#moderation-outcome").html(html);
      })
      .fail(() => {
        $("#moderation-outcome").text("Unable to load notifications.");
      });
  }

  function loadPenalties(username) {
    $.ajax({
      url: `/api/moderation/users/${encodeURIComponent(username)}/penalties`,
      method: "GET",
      headers: SilverStage.Core.authHeaders(),
    })
      .done((items) => {
        if (!items.length) {
          $("#moderation-outcome").html("No active penalties.");
          return;
        }
        const html = items
          .map(
            (p) =>
              `<div class="search-item"><div>${p.username} - ${p.penaltyType}</div><div class="search-meta">Active: ${p.active} | Ends: ${p.endsAt || "never"}</div></div>`,
          )
          .join("");
        $("#moderation-outcome").html(html);
      })
      .fail(() => {
        $("#moderation-outcome").text("Unable to load penalties.");
      });
  }

  function init() {
    $("#submit-report").on("click", function () {
      const formData = new FormData();
      formData.append("reportedUser", $("#reported-user").val());
      formData.append("contentType", $("#report-content-type").val());
      formData.append("contentRef", $("#report-content-ref").val());
      formData.append("reason", $("#report-reason").val());

      const files = $("#report-evidence")[0].files;
      for (let i = 0; i < files.length; i += 1) {
        formData.append("evidence", files[i]);
      }

      $.ajax({
        url: "/api/moderation/reports",
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        processData: false,
        contentType: false,
        data: formData,
      })
        .done((response) => {
          $("#moderation-outcome").text(
            `Report #${response.reportId} submitted. Reporter will receive outcome notification after moderation.`,
          );
          $("#report-reason").val("");
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Unable to submit report.";
          $("#moderation-outcome").text(msg);
        });
    });

    $("#load-open-reports").on("click", function () {
      loadOpenReports();
    });
    $("#load-notifications").on("click", function () {
      loadNotifications($("#notify-user").val());
    });
    $("#load-penalties").on("click", function () {
      loadPenalties($("#notify-user").val());
    });

    $(document).on("click", ".apply-decision", function () {
      const reportId = $(this).data("report-id");
      const penaltyType = $(
        `.decision-penalty[data-report-id='${reportId}']`,
      ).val();
      const decisionNotes =
        $(`.decision-notes[data-report-id='${reportId}']`).val() ||
        "Reviewed in moderation console";

      $.ajax({
        url: `/api/moderation/reports/${reportId}/decision`,
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify({ penaltyType, decisionNotes }),
      })
        .done((response) => {
          $("#moderation-outcome").text(
            `Report #${response.reportId} resolved with ${response.penaltyType}. Notifications sent to reporter and affected user.`,
          );
          loadOpenReports();
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Failed to apply moderation decision.";
          $("#moderation-outcome").text(msg);
        });
    });
  }

  SilverStage.Moderation = {
    init,
  };
})(window);
