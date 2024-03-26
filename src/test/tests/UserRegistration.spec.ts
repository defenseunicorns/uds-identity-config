import { test, expect } from '@playwright/test';
import { userLoginPage, userRegistrationForm, UserRegistrationInfo } from './utils';

// Sequential User Registration Tests ( usually defualt is running in parallel )
test.describe('Sequential User Registration Tests', () => {
  // Make these tests sequential so that user is created before login
  test.describe.configure({ mode: 'serial' });

  const userInfo: UserRegistrationInfo = {
    firstName: "John",
    lastName: "Doe",
    affiliation: "Other",
    payGrade: "N/A",
    organizationName: "Defense Unicorns",
    username: "john_doe",
    email: "johndoe@defenseunicorns.com",
    password: "PrettyUnicorns!!"
  };

  test('Registration Flow - Successful registration', async ({ page }) => {

    await userRegistrationForm(page, userInfo);
  
    // Verify on User Account Management Page
    await expect(page).toHaveTitle("Keycloak Account Management");
    await expect(page.getByRole('heading', { name: 'UDS Identity - My Account'})).toBeVisible();
  });

  test('Registration Flow - Duplicate User Creation', async ({ page }) => {
    await userRegistrationForm(page, userInfo);
  
    // Verify Error Message for User Already Exists
    await expect(page).toHaveURL(/.*registration/);
    await expect(page.getByText('Username already exists')).toBeVisible();
    await expect(page.getByText('Email already exists')).toBeVisible();
  });

  test('Login New User', async ({ page }) => {
    await userLoginPage(page);
  
    // Supply User Creds
    await page.getByLabel('Username or email', { exact: true }).fill("john_doe");
    await page.fill('#password', 'PrettyUnicorns!!');
  
    // Login User
    await page.click("#kc-login");
  
    // Verify on User Account Management Page
    await expect(page).toHaveTitle("Keycloak Account Management");
    await expect(page.getByRole('heading', { name: 'UDS Identity - My Account'})).toBeVisible();
  });
  
});

test.describe('Parallel User Registration Tests', () => {

  test('Password - Special Characters', async ({ page }) => {
    const userInfo: UserRegistrationInfo = {
      firstName: "John",
      lastName: "Doe",
      affiliation: "Other",
      payGrade: "N/A",
      organizationName: "Defense Unicorns",
      username: "john_doe",
      email: "johndoe@defenseunicorns.com",
      password: "shortpass"
    };

    await userRegistrationForm(page, userInfo);

    // Verify Error Message for User Already Exists
    await expect(page).toHaveURL(/.*registration/);
    await expect(page.getByText('Invalid password: must contain at least 2 special characters.')).toBeVisible();
  });

  test('Password - Character Length', async ({ page }) => {
    const userInfo: UserRegistrationInfo = {
      firstName: "John",
      lastName: "Doe",
      affiliation: "Other",
      payGrade: "N/A",
      organizationName: "Defense Unicorns",
      username: "john_doe",
      email: "johndoe@defenseunicorns.com",
      password: "shortpass!!"
    };

    await userRegistrationForm(page, userInfo);

    // Verify Error Message for User Already Exists
    await expect(page).toHaveURL(/.*registration/);
    await expect(page.getByText('Invalid password: minimum length 12.')).toBeVisible();
  });

});
