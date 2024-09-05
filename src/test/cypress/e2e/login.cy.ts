import { RegistrationFormData } from "../support/types";

describe("Login Flow", () => {
  it("Existing User", () => {
    // existing user created in test realm.json
    const formData: RegistrationFormData = {
      firstName: "Testing",
      lastName: "User",
      username: "testing_user",
      password: "Testingpassword1!!",
    };

    cy.loginPage();
    cy.loginUser(formData.username, formData.password);

    // Verify Successful Registration and on User Account Page
    cy.verifyLoggedIn();
  });

  it("Invalid User Creds", () => {
    const formData: RegistrationFormData = {
      username: "testing_user",
      password: "PrettyUnicorns1!!",
    };

    cy.loginPage();
    cy.loginUser(formData.username, formData.password);

    // user doesn't exist or password is incorrect
    cy.contains("span", "Invalid username or password.").should("be.visible");
  });
});
