import { RegistrationFormData } from "../support/types";

describe("Login Flow", () => {
  it("Existing User", () => {
    // existing user created in test realm.json
    const formData: RegistrationFormData = {
      firstName: "Testing",
      lastName: "User",
      username: "testing_user",
      password: "testingpassword!!",
    };

    cy.loginUser(formData.username, formData.password);

    // skip the DoD PKI Detected Pop Up
    cy.get("input#kc-cancel.btn.btn-light").click();

    // Verify Successful Registration and on User Account Page
    cy.get("#landingLoggedInUser")
      .should("be.visible")
      .and("contain", formData.firstName + " " + formData.lastName);
  });

  it("Invalid User Creds", () => {
    const formData: RegistrationFormData = {
      username: "testing_user",
      password: "PrettyUnicorns!!",
    };

    cy.loginUser(formData.username, formData.password);

    // user doesn't exist or password is incorrect
    cy.contains("span", "Invalid username or password.").should("be.visible");
  });
});
