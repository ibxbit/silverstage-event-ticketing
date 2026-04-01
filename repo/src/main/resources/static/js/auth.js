(function (window) {
  const SilverStage = window.SilverStage;

  function validatePasswordLength(password) {
    if (!password || password.length < 10) {
      $("#auth-message").text(
        "Password must be at least 10 characters before submitting.",
      );
      return false;
    }
    return true;
  }

  function handleLoginSuccess(response) {
    SilverStage.Core.resetForNewSession();
    SilverStage.Core.setAuthSession(response);
    SilverStage.Core.applyAuthorizationUi();
    $("#auth-message").text(
      `Login successful. Role: ${response.role}. Token saved for secure API calls. Visible menus: ${(response.visibleMenus || []).join(", ")}`,
    );
    SilverStage.Events.loadEvents();
  }

  function logout() {
    SilverStage.Core.clearAuthStorage();
    SilverStage.Core.resetForNewSession();
    SilverStage.Core.applyAuthorizationUi();
    $("#auth-message").text("You have been logged out. Local session data cleared.");
    $("#auth-password").val("");
    SilverStage.Events.loadEvents();
  }

  function init() {
    $("#auth-register").on("click", function () {
      const username = $("#auth-username").val() || "";
      const password = $("#auth-password").val() || "";
      const role = $("#auth-role").val() || "SENIOR";

      if (!validatePasswordLength(password)) {
        return;
      }

      $.ajax({
        url: "/api/security/accounts",
        method: "POST",
        contentType: "application/json",
        data: JSON.stringify({ username, password, role }),
      })
        .done((response) => {
          $("#auth-message").text(
            `Account ${response.username} registered successfully as ${response.role}.`,
          );
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Registration failed.";
          $("#auth-message").text(msg);
        });
    });

    $("#auth-login").on("click", function () {
      const username = $("#auth-username").val() || "";
      const password = $("#auth-password").val() || "";

      if (!validatePasswordLength(password)) {
        return;
      }

      $.ajax({
        url: "/api/security/login",
        method: "POST",
        contentType: "application/json",
        data: JSON.stringify({ username, password }),
      })
        .done((response) => {
          handleLoginSuccess(response);
        })
        .fail((xhr) => {
          const msg =
            xhr.responseJSON && xhr.responseJSON.message
              ? xhr.responseJSON.message
              : "Login failed.";
          $("#auth-message").text(msg);
        });
    });

    $("#auth-logout").on("click", function () {
      logout();
    });
  }

  SilverStage.Auth = {
    init,
    logout,
    handleLoginSuccess,
    validatePasswordLength,
  };
})(window);
