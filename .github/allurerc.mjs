// Allure 3 configuration for the backend test report.
//
// Allure 3 dropped the history/ directory Allure 2 wrote inside the report; history now lives in one
// JSON-lines file named here, and `allure generate` both reads it (to mark new, retried and flaky tests
// and to draw the trend) and appends the run to it. The report job carries this file on the Pages site.
export default {
  name: 'checkitout backend',
  output: './allure-report',
  historyPath: './history.jsonl',
};
