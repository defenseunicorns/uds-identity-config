<#import "template.ftl" as layout>
<@layout.registrationLayout headerText="Reset your password" backLink=true displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
    <#if section = "header">
        ${msg("emailForgotTitle")}
    <#elseif section = "form">

        <div class="row">
            <div class="col-lg-12">
                <div class="alert alert-info">
                    <div class="row">
                        <div class="col-lg-1 d-flex align-items-center col-alert-icon">
                            <img src="${url.resourcesPath}/img/icon_information.svg"  />
                        </div>
                        <div class="col">
                            <#if realm.duplicateEmailsAllowed>
                                ${msg("emailInstructionUsername")}
                            <#else>
                                ${msg("emailInstruction")}
                            </#if>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <form id="kc-reset-password-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="username" class="${properties.kcLabelClass!}"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="username" name="username" class="${properties.kcInputClass!}" autofocus aria-invalid="<#if messagesPerField.existsError('username')>true</#if>" dir="ltr"/>
                    <#if messagesPerField.existsError('username')>
                        <span id="input-error-username" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${messagesPerField.get('username')}
                        </span>
                    </#if>
                </div>
            </div>
            <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!} form-button-container">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doSubmit")}" id="kc-reset" disabled="disabled"/>
                </div>
            </div>
        </form>
        <script>
        // Gray the reset button out until both fields are filled
        (function () {
            function updateResetButtonState() {
                var username = document.getElementById('username');
                var resetBtn = document.getElementById('kc-reset');
                if (!username || !resetBtn) return;
                if (username.value.trim()) {
                    resetBtn.removeAttribute('disabled');
                } else {
                    resetBtn.setAttribute('disabled', 'disabled');
                }
            }
            document.addEventListener('DOMContentLoaded', function() {
                var username = document.getElementById('username');
                if (username) {
                    username.addEventListener('input', updateResetButtonState);
                    updateResetButtonState();
                }
            });
        })();
        </script>
    </#if>
</@layout.registrationLayout>
