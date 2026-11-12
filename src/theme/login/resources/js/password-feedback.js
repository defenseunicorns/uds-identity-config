/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

(() => {
    const password = document.getElementById('password-new') || document.getElementById('password');
    const confirmation = document.getElementById('password-confirm');
    if (!password || !confirmation) return;

    const requirements = document.getElementById('password-requirements');
    if (!requirements) return;
    const rules = Array.from(requirements.querySelectorAll('[data-password-rule]'));
    const pending = requirements.querySelector('[data-password-pending]');
    const progress = requirements.querySelector('[data-password-progress]');
    const match = document.getElementById('password-match');
    // Registration omits the username field when email is the username; use the email field instead.
    const username = document.getElementById('username') || document.getElementById('email');
    let confirmationBlurred = false;
    let announcement;

    // Keycloak counts UTF-16 code units, including when applying Character predicates.
    // Based on Length, MaximumLength, LowerCase, UpperCase, Digits, SpecialChars, and NotUsername policy providers:
    // https://github.com/keycloak/keycloak/tree/26.8.2/server-spi-private/src/main/java/org/keycloak/policy
    const checks = {
        length: (value, minimum) => value.length >= minimum,
        maxLength: (value, maximum) => value.length <= maximum,
        lowerCase: (value, minimum) => value.split('').filter(char => /\p{Lowercase}/u.test(char)).length >= minimum,
        upperCase: (value, minimum) => value.split('').filter(char => /\p{Uppercase}/u.test(char)).length >= minimum,
        digits: (value, minimum) => value.split('').filter(char => /\p{Decimal_Number}/u.test(char)).length >= minimum,
        specialChars: (value, minimum) => value.split('').filter(char => !/[\p{Letter}\p{Decimal_Number}]/u.test(char)).length >= minimum,
        notUsername: value => {
            const currentUsername = username ? username.value : requirements.dataset.username;
            return value.toLowerCase() !== currentUsername.toLowerCase();
        },
    };

    function updateRequirements() {
        let met = 0;
        for (const rule of rules) {
            const valid = password.value.length > 0 && checks[rule.dataset.passwordRule](password.value, Number(rule.dataset.minimum));
            rule.hidden = valid;
            if (valid) met++;
        }
        if (pending) pending.hidden = met === rules.length;
        clearTimeout(announcement);
        // Announce a summary after typing pauses instead of reading every rule on every keystroke.
        announcement = setTimeout(() => {
            if (progress) {
                progress.textContent = password.value
                    ? requirements.dataset.progress.replace('__met__', met).replace('__total__', rules.length)
                    : '';
            }
        }, 600);
    }

    function updateConfirmation() {
        const matching = password.value === confirmation.value;
        const show = confirmation.value.length > 0 && (matching || confirmationBlurred);
        match.hidden = !show;
        match.classList.toggle('is-match', matching);
        const text = show ? (matching ? match.dataset.match : match.dataset.mismatch) : '';
        if (match.textContent !== text) match.textContent = text;
    }

    function updateFeedback() {
        updateRequirements();
        updateConfirmation();
    }

    password.addEventListener('input', updateFeedback);
    confirmation.addEventListener('input', updateConfirmation);
    confirmation.addEventListener('blur', () => {
        confirmationBlurred = true;
        updateConfirmation();
    });
    // Password managers may populate fields through change events.
    password.addEventListener('change', updateFeedback);
    confirmation.addEventListener('change', updateConfirmation);
    if (username) {
        username.addEventListener('input', updateRequirements);
        username.addEventListener('change', updateRequirements);
    }

    // These checks provide guidance; Keycloak validates all policies on submission.
    updateFeedback();
})();
