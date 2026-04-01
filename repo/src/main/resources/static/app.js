$(function () {
  window.SilverStage.Events.init();
  window.SilverStage.Orders.init();
  window.SilverStage.Discovery.init();
  window.SilverStage.Moderation.init();
  window.SilverStage.Publishing.init();
  window.SilverStage.Auth.init();

  window.SilverStage.Core.resetDynamicDom();
  window.SilverStage.Core.applyAuthorizationUi();
  window.SilverStage.Events.loadEvents();
});
