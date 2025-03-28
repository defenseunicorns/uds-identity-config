<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=(realm.registrationAllowed && !registrationDisabled??); section>
    <#if section = "title">
        title
    <#elseif section = "header">
        ${kcSanitize(msg("webauthn-login-title"))?no_esc}
    <#elseif section = "form">
        <div id="kc-form-webauthn" class="${properties.kcFormClass!}">
            <form id="webauth" action="${url.loginAction}" method="post" hidden="hidden">
                <input type="hidden" id="clientDataJSON" name="clientDataJSON"/>
                <input type="hidden" id="authenticatorData" name="authenticatorData"/>
                <input type="hidden" id="signature" name="signature"/>
                <input type="hidden" id="credentialId" name="credentialId"/>
                <input type="hidden" id="userHandle" name="userHandle"/>
                <input type="hidden" id="error" name="error"/>
            </form>

            <div class="${properties.kcFormGroupClass!} no-bottom-margin">
                <#if authenticators??>
                    <form id="authn_select" class="${properties.kcFormClass!}" hidden="hidden">
                        <#list authenticators.authenticators as authenticator>
                            <input type="hidden" name="authn_use_chk" value="${authenticator.credentialId}"/>
                        </#list>
                    </form>

                    <#if shouldDisplayAuthenticators?? && shouldDisplayAuthenticators>
                        <#if authenticators.authenticators?size gt 1>
                            <p class="${properties.kcSelectAuthListItemTitle!}">
                                ${kcSanitize(msg("webauthn-available-authenticators"))?no_esc}
                            </p>
                        </#if>

                        <ul class="${properties.kcSelectAuthListClass!}" role="list">
                            <#list authenticators.authenticators as authenticator>
                                <li class="${properties.kcSelectAuthListItemWrapperClass!}">
                                    <div id="kc-webauthn-authenticator-item-${authenticator?index}" class="${properties.kcSelectAuthListItemClass!}">
                                        <div class="${properties.kcSelectAuthListItemIconClass!}">
                                            <div class="${properties.kcWebAuthnDefaultIcon!}">
                                                <svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg"
                                                        viewBox="0 0 512 512" style="width: 50px">
                                                    <path d="M336 352a176 176 0 1 0-167.7-122.3L7 391a24 24 0 0 0-7 17v80a24 24 0 0 0 24 24h80a24 24 0 0 0 24-24v-40h40a24 24 0 0 0 24-24v-40h40a24 24 0 0 0 17-7l33.3-33.3c16.9 5.4 35 8.3 53.7 8.3zm40-256a40 40 0 1 1 0 80 40 40 0 1 1 0-80z" fill="currentColor"/>
                                                </svg>
                                            </div>
                                        </div>
                                        <div class="${properties.kcSelectAuthListItemBodyClass!}">
                                            <div id="kc-webauthn-authenticator-label-${authenticator?index}"
                                                 class="${properties.kcSelectAuthListItemHeadingClass!}">
                                                ${kcSanitize(msg('${authenticator.label}'))?no_esc}
                                            </div>

                                            <#if authenticator.transports?? && authenticator.transports.displayNameProperties?has_content>
                                                <div id="kc-webauthn-authenticator-transport-${authenticator?index}"
                                                     class="${properties.kcSelectAuthListItemDescriptionClass!}">
                                                    <#list authenticator.transports.displayNameProperties as nameProperty>
                                                        <span>${kcSanitize(msg('${nameProperty!}'))?no_esc}</span>
                                                        <#if nameProperty?has_next>
                                                            <span>, </span>
                                                        </#if>
                                                    </#list>
                                                </div>
                                            </#if>

                                            <div class="${properties.kcSelectAuthListItemDescriptionClass!}">
                                                <span id="kc-webauthn-authenticator-createdlabel-${authenticator?index}">
                                                    <i>${kcSanitize(msg('webauthn-createdAt-label'))?no_esc}</i>
                                                </span>
                                                <span id="kc-webauthn-authenticator-created-${authenticator?index}">
                                                    <i>${kcSanitize(authenticator.createdAt)?no_esc}</i>
                                                </span>
                                            </div>
                                        </div>
                                        <div class="${properties.kcSelectAuthListItemFillClass!}"></div>
                                    </div>
                                </li>
                            </#list>
                        </ul>
                    </#if>
                </#if>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input id="authenticateWebAuthnButton" type="button"
                           value="${kcSanitize(msg("webauthn-doAuthenticate"))}"
                           class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"/>
                </div>
            </div>
        </div>

        <script type="module">
            import { authenticateByWebAuthn } from "${url.resourcesPath}/js/webauthnAuthenticate.js";
            const authButton = document.getElementById('authenticateWebAuthnButton');
            authButton.addEventListener("click", function() {
                const input = {
                    isUserIdentified : ${isUserIdentified},
                    challenge : '${challenge}',
                    userVerification : '${userVerification}',
                    rpId : '${rpId}',
                    createTimeout : ${createTimeout},
                    errmsg : "${msg("webauthn-unsupported-browser-text")?no_esc}"
                };
                authenticateByWebAuthn(input);
            });
        </script>

    <#elseif section = "info" && properties["WEBAUTHN_ENABLED"] == "false">
        <#if realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration">
                <span>${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
