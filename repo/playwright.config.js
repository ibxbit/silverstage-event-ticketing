const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e-tests",
  timeout: 60000,
  retries: 0,
  use: {
    baseURL: process.env.SILVERSTAGE_BASE_URL || "http://localhost:8080",
    headless: true,
  },
});
