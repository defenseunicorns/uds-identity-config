<#import "template.ftl" as layout>
    <@layout.registrationLayout headerText="signIn" displayMessage=true displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
        <#if section="form">
            <div class="row">
                <div class="col-lg-12">
                    <#if properties["X509_LOGIN_ENABLED"] == "true">
                        <form id="kc-form" action="${url.loginAction}" method="post">
                            <!-- Dynamic content is added by JavaScript -->
                        </form>
                    </#if>
                </div>
            </div>
            <#if realm.password && properties["USERNAME_PASSWORD_AUTH_ENABLED"] == "true">
                <form onsubmit="login.disabled=true;return true;" action="${url.loginAction}" method="post">
                    <div class="form-group">
                        <label class="form-label" for="username">
                            <#if !realm.loginWithEmailAllowed>
                                ${msg("username")}
                                <#elseif !realm.registrationEmailAsUsername>
                                    ${msg("usernameOrEmail")}
                                    <#else>
                                        ${msg("email")}
                            </#if>
                        </label>
                        <input tabindex="1" id="username" class="form-control " name="username"
                            value="${(login.username!'')}" type="text" autofocus autocomplete="off" />
                    </div>
                    <div class="form-group forgot-password-container">
                        <label for="password" class="form-label">
                            ${msg("password")}
                        </label>
                        <input tabindex="2" id="password" class="form-control " name="password"
                            type="password" autocomplete="off" />
                    </div>
                    <div class="form-group text-right">
                        <#if realm.resetPasswordAllowed>
                            <a tabindex="5" href="${url.loginResetCredentialsUrl}">
                                ${msg("doForgotPassword")}
                            </a>
                        </#if>
                    </div>
                    <div id="form-buttons" class="form-group form-button-container">
                        <input type="hidden" id="id-hidden-input" name="credentialId"
                            <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"
                            </#if>/>
                        <input tabindex="4" class="btn btn-primary btn-block"
                            name="login" id="kc-login" type="submit" value="${msg("doLogIn")}" disabled="disabled" />
                    </div>
                </form>
            </#if>
            <#if realm.password && social?? && social.providers?has_content && properties["SOCIAL_LOGIN_ENABLED"] == "true">
                <div id="kc-social-providers" class="kc-social-section kc-social">
                    <hr/>
                    <h2>${msg("doLogInWith")}</h2>

                    <ul class="social-ul kc-social-links <#if social.providers?size gt 3>pf-l-grid kc-social-grid</#if>">
                        <#list social.providers as p>
                            <li>
                                <a id="social-${p.alias}" class="pf-c-button pf-m-control pf-m-block kc-social-item kc-social <#if social.providers?size gt 3>pf-l-grid__item</#if>"
                                        type="button" href="${p.loginUrl}">
                                    <#if p.iconClasses?has_content>
                                        <i class="kc-social-provider-logo kc-social ${p.iconClasses!}" aria-hidden="true"></i>
                                        <span class="kc-social-provider-name kc-social-icon-text">${p.displayName!}</span>
                                    <#else>
                                        <span class="kc-social-provider-name">${p.displayName!}</span>
                                    </#if>
                                </a>
                            </li>
                        </#list>
                    </ul>
                    <hr/>
                </div>
            </#if>
            <#if properties["REGISTER_BUTTON_ENABLED"] == "true">
                <div class="footer-text">
                    ${msg("noAccountYet")} <a href="${url.registrationUrl}">${msg("registerNow")}</a><br>
                </div>
            </#if>
        </#if>
    </@layout.registrationLayout>
    <script>
    (function () {
        const feedback = document.getElementById('alert-error');
        const formContainer = document.getElementById('kc-form');

        // Dynamic content based on CAC detection
        if (feedback) {
            if (feedback.innerHTML.includes('X509 certificate') && feedback.innerHTML.includes('Invalid user')) {
                feedback.outerHTML = ``;
                formContainer.innerHTML = `
                <div class="row">
                    <div class="col-lg-12">
                        <div class="alert alert-info">
                            <div class="row">
                                <div class="col-lg-1 d-flex align-items-start col-alert-icon">
                                    <img src="${url.resourcesPath}/img/icon_information.svg" />
                                </div>
                                <div class="col">
                                    <h3>New DoD PKI Detected</h3>
                                    <p>Your CAC has been detected, but no account is associated with it</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            }
        } else if('${properties["X509_LOGIN_ENABLED"]}' == "true" && '${properties["SOCIAL_LOGIN_ENABLED"]}' == "false" && '${properties["USERNAME_PASSWORD_AUTH_ENABLED"]}' == "false"){
            // No CAC detected
            formContainer.innerHTML = `
                <div class="row">
                    <div class="col-lg-12">
                        <div class="alert alert-info">
                            <div class="row">
                                <div class="col-lg-1 d-flex align-items-start col-alert-icon">
                                    <img src="${url.resourcesPath}/img/icon_information.svg" />
                                </div>
                                <div class="col">
                                    <h3>CAC Not Detected</h3>
                                    <p>Please insert your CAC and try again.</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // Gray the login button out until both fields are filled
        function updateLoginButtonState() {
            var username = document.getElementById('username');
            var password = document.getElementById('password');
            var loginBtn = document.getElementById('kc-login');
            if (!username || !password || !loginBtn) return;
            if (username.value.trim() && password.value.trim()) {
                loginBtn.removeAttribute('disabled');
            } else {
                loginBtn.setAttribute('disabled', 'disabled');
            }
        }
        document.addEventListener('DOMContentLoaded', function() {
            var username = document.getElementById('username');
            var password = document.getElementById('password');
            if (username && password) {
                username.addEventListener('input', updateLoginButtonState);
                password.addEventListener('input', updateLoginButtonState);
                updateLoginButtonState();
            }
        });
    })();
    </script>
