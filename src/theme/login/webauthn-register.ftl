<#import "template.ftl" as layout>
<#import "password-commons.ftl" as passwordCommons>

<@layout.registrationLayout headerText="Security Key Registration" cancelButton=true; section>
    <p>
        Follow your browser’s prompts to register your security key and complete setup.
    </p>
    <p>
        Make sure your device is ready and connected.
    </p>

    <form id="register" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
        <div class="${properties.kcFormGroupClass!}">
            <input type="hidden" id="clientDataJSON" name="clientDataJSON"/>
            <input type="hidden" id="attestationObject" name="attestationObject"/>
            <input type="hidden" id="publicKeyCredentialId" name="publicKeyCredentialId"/>
            <input type="hidden" id="authenticatorLabel" name="authenticatorLabel"/>
            <input type="hidden" id="transports" name="transports"/>
            <input type="hidden" id="error" name="error"/>
            <@passwordCommons.logoutOtherSessions/>
        </div>
    </form>

    <script type="module">
        import { registerByWebAuthn } from "${url.resourcesPath}/js/webauthnRegister.js";
        const registerButton = document.getElementById('registerWebAuthn');
        registerButton.addEventListener("click", function() {
            const input = {
                challenge : '${challenge}',
                userid : '${userid}',
                username : '${username}',
                signatureAlgorithms : [<#list signatureAlgorithms as sigAlg>${sigAlg?c},</#list>],
                rpEntityName : '${rpEntityName}',
                rpId : '${rpId}',
                attestationConveyancePreference : '${attestationConveyancePreference}',
                authenticatorAttachment : '${authenticatorAttachment}',
                requireResidentKey : '${requireResidentKey}',
                userVerificationRequirement : '${userVerificationRequirement}',
                createTimeout : ${createTimeout},
                excludeCredentialIds : '${excludeCredentialIds}',
                initLabel : "${msg("webauthn-registration-init-label")?no_esc}",
                initLabelPrompt : "${msg("webauthn-registration-init-label-prompt")?no_esc}",
                errmsg : "${msg("webauthn-unsupported-browser-text")?no_esc}"
            };
            registerByWebAuthn(input);
        });
    </script>

    <input type="submit"
           class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
           id="registerWebAuthn" value="${msg("doRegisterSecurityKey")}"/>

</@layout.registrationLayout>