<#--
Copyright 2026 Defense Unicorns
SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
-->

<#macro toggle inputId label>
    <button type="button" class="password-toggle" data-password-toggle
            aria-controls="${inputId}" aria-label="${msg('passwordShowLabel', label)}"
            data-show-label="${msg('passwordShowLabel', label)}" data-hide-label="${msg('passwordHideLabel', label)}" hidden>
        <svg class="password-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
            <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/>
            <circle cx="12" cy="12" r="3"/>
        </svg>
        <svg class="password-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
            <path d="m3 3 18 18M10.6 5.1A12 12 0 0 1 12 5c6.5 0 10 7 10 7a19 19 0 0 1-3.1 4.1M6.1 6.1A20 20 0 0 0 2 12s3.5 7 10 7a12 12 0 0 0 5.9-1.9M9.9 9.9a3 3 0 0 0 4.2 4.2"/>
        </svg>
    </button>
</#macro>

<#macro rule name value>
    <li data-password-rule="${name}" data-minimum="${value?c}">
        <span class="password-rule-icon" aria-hidden="true">×</span>
        <span class="sr-only" data-rule-status></span>
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
         data-progress="${msg('passwordRequirementsProgress', '__met__', '__total__')}"
         data-met="${msg('passwordRequirementMet')}" data-unmet="${msg('passwordRequirementUnmet')}"<#if !rules?has_content> hidden</#if>>
        <#if rules?has_content>
            <p class="password-requirements-title">${msg('passwordRequirements')}</p>
            <ul role="list">
                <#list rules as name>
                    <@rule name=name value=passwordPolicies[name]/>
                </#list>
            </ul>
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
