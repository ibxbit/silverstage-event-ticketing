(function (window) {
  const SilverStage = window.SilverStage;

  function workflowContentId() {
    return Number($("#workflow-content-id").val());
  }

  function publishBodyPayload() {
    return {
      title: $("#publish-title").val() || "Untitled content",
      body: $("#publish-body").val() || "",
      summary: $("#publish-summary").val() || "Content update",
    };
  }

  function loadPublishedContent() {
    $.getJSON("/api/publishing/content")
      .done((items) => {
        if (!items.length) {
          $("#publishing-list").html("No content items found.");
          return;
        }
        const html = items
          .map(
            (item) =>
              `<div class="search-item"><strong>#${item.contentId} ${item.title}</strong><div class="search-meta">State: ${item.state} | Version: ${item.currentVersion} | Published: ${item.publishedAt || "not yet"}</div></div>`,
          )
          .join("");
        $("#publishing-list").html(html);
      })
      .fail(() => {
        $("#publishing-list").html("Unable to load publishing content.");
      });
  }

  function postWorkflow(url, successMessage) {
    $.ajax({
      url,
      method: "POST",
      headers: SilverStage.Core.authHeaders(),
    })
      .done((item) => {
        $("#publishing-output").text(successMessage(item));
        loadPublishedContent();
      })
      .fail((xhr) => {
        const msg =
          xhr.responseJSON && xhr.responseJSON.message
            ? xhr.responseJSON.message
            : "Workflow request failed.";
        $("#publishing-output").text(msg);
      });
  }

  function init() {
    $("#create-draft").on("click", function () {
      $.ajax({
        url: "/api/publishing/content",
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify({
          title: $("#publish-title").val() || "Untitled content",
          body: $("#publish-body").val() || "",
        }),
      })
        .done((item) => {
          $("#publishing-output").text(
            `Created draft #${item.contentId} in ${item.state} state.`,
          );
          loadPublishedContent();
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Unable to create draft.";
          $("#publishing-output").text(msg);
        });
    });

    $("#load-content-items").on("click", function () {
      loadPublishedContent();
    });

    $("#update-draft").on("click", function () {
      $.ajax({
        url: `/api/publishing/content/${workflowContentId()}/update`,
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify(publishBodyPayload()),
      })
        .done((item) => {
          $("#publishing-output").text(
            `Draft #${item.contentId} updated to version ${item.currentVersion}.`,
          );
          loadPublishedContent();
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Update failed.";
          $("#publishing-output").text(msg);
        });
    });

    $("#submit-content").on("click", function () {
      postWorkflow(
        `/api/publishing/content/${workflowContentId()}/submit`,
        (item) => `Content #${item.contentId} moved to ${item.state}.`,
      );
    });
    $("#review-content").on("click", function () {
      postWorkflow(
        `/api/publishing/content/${workflowContentId()}/review`,
        (item) => `Content #${item.contentId} moved to ${item.state}.`,
      );
    });
    $("#publish-content").on("click", function () {
      postWorkflow(
        `/api/publishing/content/${workflowContentId()}/publish`,
        (item) => `Content #${item.contentId} published.`,
      );
    });

    $("#request-appeal").on("click", function () {
      $.ajax({
        url: `/api/publishing/content/${workflowContentId()}/appeals`,
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify({
          justification:
            $("#publish-summary").val() || "Post-publish correction needed",
        }),
      })
        .done((appeal) => {
          $("#workflow-appeal-id").val(appeal.id);
          $("#publishing-output").text(
            `Appeal #${appeal.id} requested with status ${appeal.status}.`,
          );
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Appeal request failed.";
          $("#publishing-output").text(msg);
        });
    });

    $("#approve-appeal").on("click", function () {
      const appealId = Number($("#workflow-appeal-id").val());
      $.ajax({
        url: `/api/publishing/appeals/${appealId}/decision`,
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify({
          status: "APPROVED",
          reviewNotes: $("#publish-summary").val() || "Appeal approved",
        }),
      })
        .done((appeal) => {
          $("#publishing-output").text(
            `Appeal #${appeal.id} resolved as ${appeal.status}.`,
          );
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Appeal decision failed.";
          $("#publishing-output").text(msg);
        });
    });

    $("#apply-correction").on("click", function () {
      const contentId = workflowContentId();
      const appealId = Number($("#workflow-appeal-id").val());
      $.ajax({
        url: `/api/publishing/content/${contentId}/corrections?appealId=${appealId}`,
        method: "POST",
        headers: SilverStage.Core.authHeaders(),
        contentType: "application/json",
        data: JSON.stringify(publishBodyPayload()),
      })
        .done((item) => {
          $("#publishing-output").text(
            `Correction applied to content #${item.contentId}. Current version: ${item.currentVersion}.`,
          );
          loadPublishedContent();
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Correction failed.";
          $("#publishing-output").text(msg);
        });
    });

    $("#rollback-content").on("click", function () {
      const contentId = workflowContentId();
      const targetVersion = Number($("#workflow-rollback-version").val());
      postWorkflow(
        `/api/publishing/content/${contentId}/rollback?targetVersion=${targetVersion}`,
        (item) =>
          `Rollback applied. Content #${item.contentId} is now version ${item.currentVersion}.`,
      );
    });

    $("#show-diff").on("click", function () {
      const contentId = workflowContentId();
      const leftVersion = Number($("#workflow-left-version").val());
      const rightVersion = Number($("#workflow-right-version").val());

      $.getJSON(
        `/api/publishing/content/${contentId}/diff?leftVersion=${leftVersion}&rightVersion=${rightVersion}`,
      )
        .done((diff) => {
          const left = (diff.leftLines || []).join("<br/>");
          const right = (diff.rightLines || []).join("<br/>");
          $("#publishing-output").html(
            `<div class="diff-grid"><div><strong>v${diff.leftVersion}</strong><div class="tree">${left}</div></div><div><strong>v${diff.rightVersion}</strong><div class="tree">${right}</div></div></div>`,
          );
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Diff request failed.";
          $("#publishing-output").text(msg);
        });
    });

    $("#show-audit").on("click", function () {
      $.getJSON(`/api/publishing/content/${workflowContentId()}/audit`)
        .done((items) => {
          if (!items.length) {
            $("#publishing-output").text("No audit log entries.");
            return;
          }
          const html = items
            .map(
              (item) =>
                `<div class="search-item"><strong>${item.action}</strong><div>${item.detail}</div><div class="search-meta">${item.changedBy} | ${item.changedAt}</div></div>`,
            )
            .join("");
          $("#publishing-output").html(html);
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Audit request failed.";
          $("#publishing-output").text(msg);
        });
    });
  }

  SilverStage.Publishing = {
    init,
    loadPublishedContent,
  };
})(window);
