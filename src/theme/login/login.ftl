<#import "template.ftl" as layout>
    <@layout.registrationLayout displayMessage=true displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
        <#if section="form">
            <#if realm.password>
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
                    <div class="form-group">
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
                    <div id="form-buttons" class="form-group">
                        <input type="hidden" id="id-hidden-input" name="credentialId"
                            <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"
                            </#if>/>
                        <input tabindex="4" class="btn btn-primary btn-block"
                            name="login" id="kc-login" type="submit" value="${msg("doLogIn")}" />
                    </div>
                </form>
            </#if>
            <#if realm.password && social.providers??>
                <div id="kc-social-providers" class="kc-social-section kc-social-gray">
                    <hr/>
                    <h2>${msg("identity-provider-login-label")}</h2>

                    <ul class="social-ul kc-social-links <#if social.providers?size gt 3>pf-l-grid kc-social-grid</#if>">
                        <#list social.providers as p>
                            <li>
                                <a id="social-${p.alias}" class="pf-c-button pf-m-control pf-m-block kc-social-item kc-social-gray <#if social.providers?size gt 3>pf-l-grid__item</#if>"
                                        type="button" href="${p.loginUrl}">
                                    <#if p.iconClasses?has_content>
                                        <i class="kc-social-provider-logo kc-social-gray ${p.iconClasses!}" aria-hidden="true"></i>
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
            <div class="footer-text">
                No account? <a href="${url.registrationUrl}">Click here</a> to register now.<br>
            </div>
        </#if>
    </@layout.registrationLayout>
    <script>
    const feedback = document.getElementById('alert-error');
    if (feedback && feedback.innerHTML.indexOf('X509 certificate') > -1 && feedback.innerHTML.indexOf('Invalid user') > -1) {
        feedback.outerHTML = [
            '<div class="alert alert-info cac-info">',
            '<h2>New DoD PKI Detected</h2>',
            '<div style="line-height: 2rem;">If you do not have an account yet, <a href="${url.registrationUrl}">click to register</a> now.  Otherwise, please login with your username/password to associate this CAC with your existing account.',
            '</div></div>'
        ].join('');
    }
    </script>
