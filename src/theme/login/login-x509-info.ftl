<#import "template.ftl" as layout>
    <@layout.registrationLayout backButton=true; section>
        <#if section="form">
            <form id="kc-x509-login-info" class="" action="${url.loginAction}" method="post">
                <div class="form-group">
                    <div class="row">
                        <div class="col-lg-12">
                            <div class="alert alert-info">
                                <div class="row">
                                    <div class="col-lg-1 d-flex align-items-start col-alert-icon">
                                        <img src="${url.resourcesPath}/img/icon_information.svg" />
                                    </div>
                                    <div class="col">
                                        <h3>DoD PKI Detected</h3>
                                        <#if x509.formData.subjectDN??>
                                            <p id="certificate_subjectDN" class="">
                                                ${(x509.formData.subjectDN!"")}
                                            </p>
                                        <#-- Properly display subjectDN on initial registration -->
                                        <#elseif x509.formData.cacSubjectDN??>
                                            <p id="certificate_subjectDN" class="">
                                                ${(x509.formData.cacSubjectDN!"")}
                                            </p>
                                        <#else>
                                            <p id="certificate_subjectDN" class="">
                                                ${msg("noCertificate")}
                                            </p>
                                        </#if>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="form-group">
                    <#if x509.formData.isUserEnabled??>
                        <label for="username" class="">
                            ${msg("doX509Login")}
                        </label>
                        <label id="username" class="font-weight-bold">
                            ${(x509.formData.username!'')}
                        </label>
                    </#if>
                </div>
                <div class="form-group">
                    <div id="kc-form-buttons" class="">
                        <div class="text-right">
                            <input class="btn btn-primary btn-block" name="login" id="kc-login" type="submit" value="${msg("doContinue")}" autofocus />
                        </div>
                    </div>
                </div>
            </form>
        </#if>
        </@layout.registrationLayout>
