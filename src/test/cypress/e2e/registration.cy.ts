import { RegistrationFormData } from "../support/types";

describe("CAC Registration Flow", () => {
  const formData: RegistrationFormData = {
    firstName: "John",
    lastName: "Doe",
    organization: "Defense Unicorns",
    username: "john_doe",
    email: "johndoe@defenseunicorns.com",
    password: "CAC",
    affiliation: "Contractor",
    payGrade: "N/A",
  };

  it("Successful CAC Registration", () => {
    cy.registrationPage(formData);

    // Verify Successful Registration and on User Account Page
    cy.get("#landingLoggedInUser")
      .should("be.visible")
      .and("contain", formData.firstName + " " + formData.lastName);
  });

  it("Successfull Login of CAC Registered User", () => {
    // Navigate to login page
    cy.loginPage();

    // Verify DoD PKI Detected Banner on Login page
    cy.get(".form-group .alert-info").should("be.visible").contains("h2", "DoD PKI Detected");
    cy.get(".form-group #certificate_subjectDN")
      .should("be.visible")
      .contains("C=US,ST=Colorado,L=Colorado Springs,O=Defense Unicorns,CN=uds.dev");

    // Verify that PKI User information is correct
    cy.get(".form-group").contains("label", "You will be logged in as:").should("be.visible");
    cy.get(".form-group #username").should("be.visible").contains("john_doe");

    // Sign in using the PKI
    cy.get("#kc-login").should("be.visible").click();

    // Verify Users first and last in top bar
    cy.get("#landingLoggedInUser").should("be.visible").and("have.text", "John Doe");

    // Verify that groups card is present, proving that signin was successful
    cy.get(".pf-c-card__title .pf-u-display-flex").should("exist").and("contain", "Groups");
    cy.get(".pf-c-card__body").should("exist");
    cy.get("#landing-groups").should("exist");

    // intercept the request that gets users personal info and verify mattermost id exists
    cy.intercept('GET', 'https://sso.uds.dev/realms/uds/account/', (req) => {
      req.continue((res) => {
        if(res.body && res.body.attributes) {
          // Check if 'mattermostid' attribute exists in the attributes object
          const mattermostIdExists = res.body.attributes.hasOwnProperty('mattermostid');
          // Assert that 'mattermostid' attribute exists
          expect(mattermostIdExists).to.be.true;
        } else {
          // Fail the test if the attributes field is missing
          expect(res.body.attributes).to.exist;
        }
      })
    });

    // Verify that personal info button exists and click it
    cy.visit('/realms/uds/account/#/personal-info');
  });
});

describe("Registration Tests", () => {
  it("Duplicate Registration", () => {
    const formData: RegistrationFormData = {
      firstName: "Testing",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "testing_user",
      email: "testinguser@gmail.com",
      password: "PrettyUnicorns!!",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    cy.registrationPage(formData);

    // duplicate user trying to register with PKI should result in this warning
    cy.contains("span.message-details", "Email already exists.").should("be.visible");
    cy.contains("span.message-details", "Username already exists.").should("be.visible");
  });

  it("Password Length", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "Pretty!!",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    cy.registrationPage(formData);

    // password isn't long enough
    cy.contains("span.message-details", "Invalid password: minimum length 12.").should(
      "be.visible",
    );
  });

  it("Password Complexity", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "PrettyUnicorns",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    cy.registrationPage(formData);

    // password isn't complex enough
    cy.contains(
      "span.message-details",
      "Invalid password: must contain at least 2 special characters.",
    ).should("be.visible");
  });
});
