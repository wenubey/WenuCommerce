/** @type {import('jest').Config} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  testMatch: ["**/test/**/*.test.ts"],
  testTimeout: 30000,
  // ts-jest reads tsconfig.json by default. Keep transform minimal.
};
