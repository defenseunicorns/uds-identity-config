<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false cancelButton=true; section>
    <#if section = "header">
        ${msg("deleteCredentialTitle", credentialLabel)}
    <#elseif section = "form">
        <div id="kc-delete-text">
            ${msg("deleteCredentialMessage", credentialLabel)}
        </div>
        <p></p>
        <form class="form-actions" action="${url.loginAction}" method="POST">
            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="accept" id="kc-accept" type="submit" value="${msg("doConfirmDelete")}"/>
        </form>
        <div class="clearfix"></div>
    </#if>
</@layout.registrationLayout>