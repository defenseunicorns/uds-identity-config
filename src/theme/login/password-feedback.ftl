<#--
Copyright 2026 Defense Unicorns
SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
-->

<#macro rule name value>
    <li data-password-rule="${name}" data-minimum="${value?c}">
        <span class="password-rule-icon" aria-hidden="true">×</span>
        <span>${msg('passwordRule' + name?cap_first + (value == 1)?then('One', ''), value)}</span>
    </li>
</#macro>

<#macro requirements>
    <#assign rules = []>
    <#if passwordPolicies??>
        <#list ['length', 'maxLength', 'lowerCase', 'upperCase', 'digits', 'specialChars'] as name>
            <#if passwordPolicies[name]?? && passwordPolicies[name] gt 0>
                <#assign rules = rules + [name]>
            </#if>
        </#list>
    </#if>
    <div id="password-requirements" class="password-requirements"
         data-progress="${msg('passwordRequirementsProgress', '__met__', '__total__')}"<#if !rules?has_content> hidden</#if>>
        <#if rules?has_content>
            <div data-password-pending>
                <p class="password-requirements-title">${msg('passwordRequirements')}</p>
                <ul role="list">
                    <#list rules as name>
                        <@rule name=name value=passwordPolicies[name]/>
                    </#list>
                </ul>
            </div>
            <p class="sr-only" data-password-progress role="status" aria-live="polite" aria-atomic="true"></p>
        </#if>
    </div>
</#macro>

<#macro confirmation>
    <p id="password-match" class="password-match" role="status" aria-live="polite" aria-atomic="true"
       data-match="${msg('passwordsMatch')}" data-mismatch="${msg('passwordsDoNotMatch')}" hidden></p>
</#macro>

<#macro script>
    <script src="${url.resourcesPath}/js/password-feedback.js" defer></script>
</#macro>
