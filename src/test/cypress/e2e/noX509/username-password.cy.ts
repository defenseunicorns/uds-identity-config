import { RegistrationFormData } from "../../support/types";

describe("Username / Password Tests", () => {
  const formData: RegistrationFormData = {
    firstName: "Test",
    lastName: "Test",
    organization: "Defense Unicorns",
    username: "test",
    email: "test@gmail.com",
    password: "Testingpassword1!!",
    affiliation: "Contractor",
    payGrade: "N/A",
  };

  it("User Registration - Success", () => {
    // go to registration page
    cy.registrationPage();

    // register user
    cy.registerUser(formData);

    // setup OTP for registered user
    cy.setupOTP(formData.username);

    cy.verifyUserAccountPage(formData);
  });

  it("User Login - Success", () => {
    // login user
    cy.loginPage();

    cy.loginUser(formData.username, formData.password);

    // Avoid using the same OTP twice for registration and login
    cy.wait(30000);

    // enter users OTP
    cy.enterOTP(formData.username);

    cy.verifyUserAccountPage(formData);

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
  });

  it("Invalid Password Login - Failure", () => {
    cy.loginPage();

    cy.loginUser(formData.username, "testingPassword1!!");

    // user doesn't exist or password is incorrect
    cy.contains("span", "Invalid username or password.").should("be.visible");
  });

  it("Invalid Duplicate User - Failue", () => {
    // go to registration page
    cy.registrationPage();

    // register user
    cy.registerUser(formData);

    // duplicate user trying to register with PKI should result in this warning
    cy.contains("span.message-details", "Email already exists.").should("be.visible");
    cy.contains("span.message-details", "Username already exists.").should("be.visible");
  });

  it("Invalid Password Length - Failue", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "Pretty1!!",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

      registration(formData, "Invalid password: minimum length 15.");
  });

  it("Invalid Password Complexity ( Special Characters ) - Failure", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "PrettyUnicorns1",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    registration(formData, "Invalid password: must contain at least 2 special characters.");
  });

  it("Invalid Password Complexity ( Digits ) - Failure", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "PrettyUnicorns!!",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    registration(formData, "Invalid password: must contain at least 1 numerical digits");
  });

  it("Invalid Password Complexity ( Uppercase ) - Failure", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "prettyunicorns1!!",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    registration(formData, "Invalid password: must contain at least 1 upper case characters.");
  });

  it("Invalid Password Complexity ( Lowercase ) - Failure", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "PRETTYUNICORNS1!!",
      affiliation: "Contractor",
      payGrade: "N/A",
    };

    registration(formData, "Invalid password: must contain at least 1 lower case characters.");
  });
});

function registration(formData: RegistrationFormData, message: string) {
    // go to registration page
    cy.registrationPage();

    // register user
    cy.registerUser(formData);

    cy.contains(
      "span.message-details",
      message,
    ).should("be.visible");
}