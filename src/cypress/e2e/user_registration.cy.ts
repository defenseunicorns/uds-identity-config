describe('PKI Registration Flow', () => {

    const formData = {
        firstName: 'John',
        lastName: 'Doe',
        organization: 'Defense Unicorns',
        username: 'john_doe',
        email: 'johndoe@defenseunicorns.com',
        password: 'CAC',
        affiliation: 'Contractor',
        payGrade: 'N/A'
      };

    it('Successful Registration', () => {
        cy.registrationPage(formData);

        // Verify Successful Registration and on User Account Page
        cy.get('#landingLoggedInUser').should('be.visible').and('contain', formData.firstName+ ' ' + formData.lastName);
    });

    // TODO: this doesnt work because of CAC registration ATM
    // it('Successfull Login of Register User', () => {
    //     cy.loginUser(formData.username, formData.password);

    //     // Verify Successful Registration and on User Account Page
    //     cy.get('#landingLoggedInUser').should('be.visible').and('contain', formData.firstName+ ' ' + formData.lastName);
    // });
});

describe('Registration Tests', () => {

    it('Duplicate Registration', () => {
        const formData = {
            firstName: 'Testing',
            lastName: 'User',
            organization: 'Defense Unicorns',
            username: 'testing_user',
            email: 'testinguser@gmail.com',
            password: 'PrettyUnicorns!!',
            affiliation: 'Contractor',
            payGrade: 'N/A'
        };

        cy.registrationPage(formData);

        // duplicate user trying to register with PKI should result in this warning
        cy.contains('span.message-details', 'Email already exists.').should('be.visible');
        cy.contains('span.message-details', 'Username already exists.').should('be.visible');
    });

    it('Password Length', () => {
        const formData = {
            firstName: 'New',
            lastName: 'User',
            organization: 'Defense Unicorns',
            username: 'new_user',
            email: 'newuser@gmail.com',
            password: 'Pretty!!',
            affiliation: 'Contractor',
            payGrade: 'N/A'
        };

        cy.registrationPage(formData);

        // password isn't long enough
        cy.contains('span.message-details', 'Invalid password: minimum length 12.').should('be.visible');
    });

    it('Password Complexity', () => {
        const formData = {
            firstName: 'New',
            lastName: 'User',
            organization: 'Defense Unicorns',
            username: 'new_user',
            email: 'newuser@gmail.com',
            password: 'PrettyUnicorns',
            affiliation: 'Contractor',
            payGrade: 'N/A'
        };

        cy.registrationPage(formData);

        // password isn't complex enough
        cy.contains('span.message-details', 'Invalid password: must contain at least 2 special characters.').should('be.visible');
    });    
});