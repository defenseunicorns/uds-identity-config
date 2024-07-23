import { RegistrationFormData } from "../../support/types";

describe("x509 Tests", () => {
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

    it("Register New User", () => {
        // go to registration page
        cy.loginPage();
        cy.avoidX509();

        // Verify the presence of the registration link and then click
        cy.contains(".footer-text a", "Click here").should("be.visible").click();

        // Verify client cert has been loaded properly by this header being present
        cy.contains("h2", "DoD PKI User Registration").should("be.visible");

        // register user
        cy.registerUser(formData);

        // setup OTP for registered user
        cy.setupOTP(formData.username);

        cy.verifyUserAccountPage(formData);
    });

    it("User New Login", () => {
        // login user
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

        // Verify that personal info button exists and click it
        cy.visit('/realms/uds/account/#/personal-info');
    });
});