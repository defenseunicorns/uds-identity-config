import { test, expect } from '@playwright/test';
import { userLoginPage } from './utils';

test.describe('User Login Tests', () => {

  test('Login Flow - Existing User', async ({ page }) => {
    await userLoginPage(page);

    // Supply User Creds
    await page.getByLabel('Username or email', { exact: true }).fill("testing_user");
    await page.fill('#password', 'testingpassword!!');
  
    // Login User
    await page.click("#kc-login");
  
    // Verify on User Account Management Page
    await expect(page).toHaveTitle("Keycloak Account Management");
    await expect(page.getByRole('heading', { name: 'UDS Identity - My Account'})).toBeVisible();
  });
  
  test('Login Flow - Nonexistant User / Incorrect credentials', async ({ page }) => {
    await userLoginPage(page);
    
    // Supply User Creds
    await page.getByLabel('Username or email', { exact: true }).fill("fake_user");
    await page.fill('#password', 'fakepassword!!');
    // Login User
    await page.click("#kc-login");
  
    // Verify on User Account Management Page
    await expect(page.getByText('Invalid username or password.')).toBeVisible();
    // Verify still on login page
    await expect(page).toHaveURL(/.*authenticate/);
    await expect(page.getByRole('button', {name: 'Log In'})).toBeVisible();
  });

});
