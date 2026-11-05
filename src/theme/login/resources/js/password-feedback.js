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
    const progress = requirements.querySelector('[data-password-progress]');
    const match = document.getElementById('password-match');
    let confirmationBlurred = false;
    let announcement;

    // Keycloak counts UTF-16 code units, including when applying Character predicates.
    const checks = {
        length: (value, minimum) => value.length >= minimum,
        maxLength: (value, maximum) => value.length <= maximum,
        lowerCase: (value, minimum) => value.split('').filter(char => /\p{Lowercase}/u.test(char)).length >= minimum,
        upperCase: (value, minimum) => value.split('').filter(char => /\p{Uppercase}/u.test(char)).length >= minimum,
        digits: (value, minimum) => value.split('').filter(char => /\p{Decimal_Number}/u.test(char)).length >= minimum,
        specialChars: (value, minimum) => value.split('').filter(char => !/[\p{Letter}\p{Decimal_Number}]/u.test(char)).length >= minimum,
    };

    function updateRequirements() {
        let met = 0;
        for (const rule of rules) {
            const valid = password.value.length > 0 && checks[rule.dataset.passwordRule](password.value, Number(rule.dataset.minimum));
            rule.classList.toggle('is-met', valid);
            rule.querySelector('.password-rule-icon').textContent = valid ? '✓' : '×';
            rule.querySelector('[data-rule-status]').textContent = password.value ? `${valid ? requirements.dataset.met : requirements.dataset.unmet} ` : '';
            if (valid) met++;
        }
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

    for (const toggle of document.querySelectorAll('[data-password-toggle]')) {
        const input = document.getElementById(toggle.getAttribute('aria-controls'));
        toggle.hidden = false;
        toggle.addEventListener('click', () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            toggle.classList.toggle('is-visible', show);
            toggle.setAttribute('aria-label', show ? toggle.dataset.hideLabel : toggle.dataset.showLabel);
        });
    }

    password.form.addEventListener('submit', () => {
        password.type = 'password';
        confirmation.type = 'password';
    });

    // These checks provide guidance; Keycloak validates all policies on submission.
    updateFeedback();
})();
