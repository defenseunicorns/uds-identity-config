<#import "template.ftl" as layout>
<@layout.registrationLayout backLink=true displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <#if section = "header">
        ${msg("updatePasswordTitle")}
    <#elseif section = "form">
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
                    <div class="col-lg-12">
                        <div class="row align-items-center">
                            <label class="custom-checkbox">
                                <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on">
                                <span></span>
                            </label>
                            <label for="termsCheckbox" style="margin-left: 10px;">
                                ${msg("logoutOtherSessions")}
                            </label>
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
