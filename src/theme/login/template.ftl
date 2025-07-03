<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true headerText="" backButton=false backLink=false>
    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
    <html xmlns="http://www.w3.org/1999/xhtml" class="${properties.kcHtmlClass!}">

    <head>
        <meta charset="utf-8">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <meta name="robots" content="noindex, nofollow">
        <#if properties.meta?has_content>
            <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>
${msg("loginTitle",(realm.displayName!''))}
</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.png" />
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>
<body class="${properties.kcBodyClass!}">
    <div class="container-fluid">
        <div class="row justify-content-center">
            <div class="col-xl-5 col-lg-7 col-md-10">
                <div class="card">
                    <div class="card-header branding row">
                        <div class="col-12 text-center d-block d-sm-none mb-3">
                            <img src="${url.resourcesPath}/img/logo.png" class="img-fluid" alt="Logo"/>
                        </div>
                        <div class="col-sm-5 p-0 d-none d-sm-block">
                            <img src="${url.resourcesPath}/img/logo.png" class="img-fluid" alt="Logo"/>
                        </div>
                        <div class="col-sm-1 d-none d-sm-block">&nbsp;</div>
                        <div class="col-12 col-sm-6 my-auto text-center">
                            <#if client?? && client.name?has_content>
                                <#-- Check if the client name matches the specific entry -->
                                <#if client.name == "${" + "client_account-console" + "}">
                                    <h2 class="client-unique-name">
                                        My Account
                                    </h2>
                                <#elseif client.name == "${" + "client_security-admin-console" + "}">
                                    <h2 class="client-unique-name">
                                        Admin Account
                                    </h2>
                                <#else>
                                    <h2 class="client-unique-name">
                                        ${kcSanitize(client.name)?no_esc}
                                    </h2>
                                </#if>
                            <#else>
                                <h2 class="client-unique-name">
                                    ${kcSanitize(msg("loginTitleHtml",(realm.displayNameHtml!'')))?no_esc}
                                </h2>
                            </#if>
                        </div>
                    </div>
                    <br>
                    <div class="card-body">
                        <#if backButton || backLink>
                            <div class="row">
                                <div class="col-lg-12">
                                    <#if backButton>
                                        <form class="" action="${url.loginAction}" method="post">
                                            <img src="${url.resourcesPath}/img/icon_back.svg" />
                                            <input name="cancel" id="kc-cancel" type="submit" value="${kcSanitize(msg("backToLogin"))?no_esc}" class="btn-text" />
                                        </form>
                                    </#if>
                                    <#if backLink>
                                        <a type="submit" href="${url.loginUrl}">
                                            <img src="${url.resourcesPath}/img/icon_back.svg" />
                                            ${kcSanitize(msg("backToLogin"))?no_esc}
                                        </a>
                                    </#if>
                                </div>
                            </div>
                            <br/>
                        </#if>

                        <#if headerText?has_content>
                            <div class="row">
                                <div class="col-lg-12">
                                    <h3>${msg(headerText)}</h3>
                                    <hr/>
                                </div>
                            </div>
                        </#if>

                        <#-- App-initiated actions should not see warning messages about the need to complete the action -->
                        <#-- during login.                                                                               -->
                        <#if displayMessage && message?has_content && (message.type != ' warning' || !isAppInitiatedAction??)>
                            <div class="row">
                                <div class="col-lg-12">
                                    <div id="alert-error" class="alert alert-<#if message.type = 'error'>error<#else>warning</#if>">
                                        <div class="row">
                                            <div class="col-lg-1 align-items-center d-flex col-alert-icon">
                                                <img src="${url.resourcesPath}/img/icon_<#if message.type = 'error'>error<#else>warning</#if>.svg" />
                                            </div>
                                            <div class="col">
                                                <p>${kcSanitize(message.summary)?no_esc}</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
        </#if>
        <#nested "form">
            <#if displayInfo>
                <div id="kc-info" class="${properties.kcSignUpClass!} ">
                    <div id="kc-info-wrapper">
                        <#nested "info">
                    </div>
                </div>
            </#if>
            </div>
            </div>
            </div>
            </div>
            </div>
            <footer class="fixed-footer">
                <img src="${url.resourcesPath}/img/footer.png" alt="Footer" />
            </footer>
            </body>

    </html>
</#macro>
