<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true cancelButton=true headerText="Security Key Error"; section>
    <script type="text/javascript">
        refreshPage = () => {
            document.getElementById('isSetRetry').value = 'retry';
            document.getElementById('executionValue').value = '${execution}';
            document.getElementById('kc-error-credential-form').requestSubmit();
        }
    </script>

    <form id="kc-error-credential-form" class="${properties.kcFormClass!}" action="${url.loginAction}"
          method="post">
        <input type="hidden" id="executionValue" name="authenticationExecution"/>
        <input type="hidden" id="isSetRetry" name="isSetRetry"/>
    </form>

    <input tabindex="4" onclick="refreshPage()" type="button"
           class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
           name="try-again" id="kc-try-again" value="${kcSanitize(msg("doTryAgain"))?no_esc}"
    />
</@layout.registrationLayout>