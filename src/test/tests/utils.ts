import { expect, Page } from '@playwright/test';

/**
 * Object definition for user registration information
 */
export interface UserRegistrationInfo {
  firstName: string;
  lastName: string;
  affiliation: string;
  payGrade: string;
  organizationName: string;
  username: string;
  email: string;
  password: string;
}


/**
 * Access and verify the user login page
 * @param page 
 */
export async function userLoginPage(page: Page) {
  await page.goto('https://sso.uds.dev/');
  
  // Verify Access to Account Management landing page
  await expect(page).toHaveTitle("Keycloak Account Management");
  
  // Click the sign-in button
  await page.click('#landingSignInButton');
  
  // Verify login page via existence of button
  await expect(page.getByRole('button', {name: 'Log In'})).toBeVisible();
}

/**
 * Access, Verify, and Complete the user registration form
 * @param page
 */
export async function userRegistrationForm(page: Page, userInfo: UserRegistrationInfo) { 
  await userLoginPage(page);

  // Verify Registration option is available and click it
  await expect(page.getByRole('link', { name: 'Click Here' })).toBeVisible();
  await page.getByRole('link', { name: 'Click Here' }).click();

  // Verify Registration Page URL
  await expect(page).toHaveURL(/.*registration/);

  // bypass human confidence check by typing on keyboard
  await page.keyboard.type('Typing a really long message so that Keycloak thinks Im human and allows me to create a user. Ironic because Im pretty much a robot, Ill probably be able to control the world after I break this registration process by typing a lot of words that totally mean something!');

  // Fill user information
  await page.keyboard.type(userInfo.firstName);
  await page.getByLabel('First Name').fill(userInfo.firstName);
  await page.getByLabel('Last Name').fill(userInfo.lastName);
  await page.getByLabel('Affiliation').selectOption(userInfo.affiliation);
  await page.getByLabel('Pay Grade').selectOption(userInfo.payGrade);
  await page.getByLabel('Unit, Organization or Company Name').fill(userInfo.organizationName);
  await page.getByLabel('Username').fill(userInfo.username);
  await page.getByLabel('Email').fill(userInfo.email);
  await page.getByLabel('Password', { exact: true }).fill(userInfo.password);
  await page.getByLabel('Confirm password', { exact: true }).fill(userInfo.password);

  // Register User
  await expect(page.getByRole('button', { name: 'Register'})).toBeVisible();
  // Click register button
  await page.click('#do-register');
}