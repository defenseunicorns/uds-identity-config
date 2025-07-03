<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <div class="back-button-container">
        <a href="/realms/${realm.name}/account/" class="back-button">
            <img src="${url.resourcesPath}/img/icon_back.svg" alt=""/>
            <span>${msg("backToAccountConsole")}</span>
        </a>
    </div>
    <h4>${msg("updatePasswordTitle")}</h4>
    <hr class="form-separator">
    <#if section = "form">
        <form id="kc-passwd-update-form" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="password-new" class="${properties.kcLabelClass!}">${msg("passwordNew")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <div class="${properties.kcInputGroup!}" dir="ltr">
                        <input type="password" id="password-new" name="password-new" class="${properties.kcInputClass!}"
                               autofocus autocomplete="new-password"
                               aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                        />
                    </div>

                    <#if messagesPerField.existsError('password')>
                        <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('password'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="password-confirm" class="${properties.kcLabelClass!}">${msg("passwordConfirm")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <div class="${properties.kcInputGroup!}" dir="ltr">
                        <input type="password" id="password-confirm" name="password-confirm"
                               class="${properties.kcInputClass!}"
                               autocomplete="new-password"
                               aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                        />
                    </div>

                    <#if messagesPerField.existsError('password-confirm')>
                        <span id="input-error-password-confirm" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                        </span>
                    </#if>

                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="col-lg-12 no-padding-sides">
                        <div class="logout-sessions-container">
                            <div class="checkbox-wrapper">
                                <div class="logout-checkbox-wrapper">
                                    <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" class="logout-checkbox" checked>
                                    <span class="logout-checkbox-custom"></span>
                                </div>
                            </div>
                            <div class="logout-sessions-content">
                                <label for="logout-sessions" class="logout-checkbox-label">
                                    ${msg("logoutOtherSessions")}
                                </label>
                                <div class="checkbox-description">
                                    This will end all your active sessions on other devices for security.
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <#if isAppInitiatedAction??>
                        <input id="kc-accept" name="login" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doSubmit")}" />
                    <#else>
                        <input id="kc-accept" name="login" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doSubmit")}" />
                    </#if>
                </div>
            </div>
        </form>
        <script>
            // Gray the reset button out until both fields are filled
            (function () {
                function updateResetButtonState() {
                    var password = document.getElementById('password-new');
                    var passwordConfirm = document.getElementById('password-confirm');
                    var submitButton = document.getElementById('kc-accept');
                    if ((!password && !passwordConfirm) || !submitButton) return;
                    if (password.value.trim() && passwordConfirm.value.trim()) {
                        submitButton.removeAttribute('disabled');
                    } else {
                        submitButton.setAttribute('disabled', 'disabled');
                    }
                }

                const feedback = document.getElementById('alert-error');

                // Remove the message sent by Keycloak. It's not needed here.
                if (feedback && feedback.innerHTML.includes('You need to change your password.')) {
                    feedback.innerHTML = '';
                    feedback.outerHTML = '';
                }

                document.addEventListener('DOMContentLoaded', function() {
                    var password = document.getElementById('password-new');
                    var passwordConfirm = document.getElementById('password-confirm');
                    if (password) {
                        password.addEventListener('input', updateResetButtonState);
                        updateResetButtonState();
                    }
                    if (passwordConfirm) {
                        passwordConfirm.addEventListener('input', updateResetButtonState);
                        updateResetButtonState();
                    }
                });
            })();
        </script>
    </#if>
</@layout.registrationLayout>
