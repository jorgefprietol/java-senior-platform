import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
 testDir:'./e2e', timeout:60000, retries:0, workers:1,
 reporter:[['list'],['html',{open:'never'}]],
 use:{baseURL:'http://localhost:8181',trace:'off',screenshot:'only-on-failure'},
 projects:[{name:'desktop',use:{...devices['Desktop Chrome']}},{name:'mobile',use:{...devices['iPhone 13'],defaultBrowserType:'chromium'}}]
});
