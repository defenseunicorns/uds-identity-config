<#import "template.ftl" as layout>
    <@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm', 'affiliation', 'rank', 'organization', 'notes'); section>
        <#if section="form">
            <form action="/chuck-norris-calendar-goes-straight-from-march-31st-to-april-2nd-because-no-one-fools-chuck-norris"
                id="unicorn-registration-form" method="post">
                <div class="back-button-container">
                    <a href="${url.loginUrl}" class="back-button">
                        <img src="${url.resourcesPath}/img/icon_back.svg" alt=""/>
                        <span>${msg("backToLogin")}</span>
                    </a>
                </div>
                <h4>${msg("registerNewAccount")}</h4>
                <hr class="form-separator">
                <#if cacSubjectDN??>
                    <div class="row">
                        <div class="col-lg-12">
                            <div class="alert alert-info">
                                <div class="row">
                                    <div class="col-lg-1 align-items-start d-flex col-alert-icon">
                                        <img src="${url.resourcesPath}/img/icon_information.svg" />
                                    </div>
                                    <div class="col">
                                        <h3>DoD PKI User Registration</h3>
                                        <p>${cacSubjectDN}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                <#else>
                    <#if properties["USERNAME_PASSWORD_AUTH_ENABLED"] == "false" && properties["X509_LOGIN_ENABLED"] == "true">
                        <div class="row">
                            <div class="col-lg-12">
                                <div class="alert alert-warning">
                                    <div class="row">
                                        <div class="col-lg-1 align-items-start d-flex col-alert-icon">
                                            <img src="${url.resourcesPath}/img/icon_warning.svg" />
                                        </div>
                                        <div class="col">
                                            <h3>Registration Requirement</h3>
                                            <p>No CAC detected. A DoD PKI CAC is required to complete registration.</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </#if>
                </#if>

                <div class="row">
                    <div class="col-lg-12">
                        <div class="alert alert-info">
                            <div class="row">
                                <div class="col-lg-1 d-flex align-items-center col-alert-icon">
                                    <img src="${url.resourcesPath}/img/icon_information.svg"  />
                                </div>
                                <div class="col">
                                    <p>Use your company or government email when registering.</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="row">
                    <div class="col-lg-6 form-group ${messagesPerField.printIfExists('firstName','has-error')}">
                        <label for="firstName" class="form-label">
                            ${msg("firstName")}
                        </label>
                        <input type="text" id="firstName" class="form-control" name="firstName"
                            value="${(register.formData.firstName!'')}" />
                        <#if messagesPerField.existsError('firstName')>
                            <span class="message-details" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('firstName'))?no_esc}
                            </span>
                        </#if>
                    </div>
                    <div class="col-lg-6 form-group ${messagesPerField.printIfExists('lastName','has-error')}">
                        <label for="lastName" class="form-label">
                            ${msg("lastName")}
                        </label>
                        <input type="text" id="lastName" class="form-control" name="lastName"
                            value="${(register.formData.lastName!'')}" />
                        <#if messagesPerField.existsError('lastName')>
                            <span class="message-details" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('lastName'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </div>
                <#if properties["ENABLE_REGISTRATION_FIELDS"] == "true">
                    <div class="row">
                        <div class="col-lg-6 form-group ${messagesPerField.printIfExists('affiliation','has-error')}">
                            <label for="affiliation" class="form-label">Affiliation</label>
                            <select id="affiliation" name="affiliation" class="form-control">
                                <option selected disabled hidden>Select your org</option>
                                <optgroup label="US Government">
                                    <option>US Air Force</option>
                                    <option>US Air Force Reserve</option>
                                    <option>US Air National Guard</option>
                                    <option>US Army</option>
                                    <option>US Army Reserve</option>
                                    <option>US Army National Guard</option>
                                    <option>US Coast Guard</option>
                                    <option>US Coast Guard Reserve</option>
                                    <option>US Marine Corps</option>
                                    <option>US Marine Corps Reserve</option>
                                    <option>US Navy</option>
                                    <option>US Navy Reserve</option>
                                    <option>US Space Force</option>
                                    <option>Dept of Defense</option>
                                    <option>Federal Government</option>
                                    <option>Other</option>
                                </optgroup>
                                <optgroup label="Contractor">
                                    <option>A&AS</option>
                                    <option>Contractor</option>
                                    <option>FFRDC</option>
                                    <option>Other</option>
                                </optgroup>
                            </select>
                            <#if messagesPerField.existsError('affiliation')>
                                <span class="message-details" aria-live="polite">
                                    ${kcSanitize(messagesPerField.get('affiliation'))?no_esc}
                                </span>
                            </#if>
                        </div>
                        <div class="col-lg-6 form-group ${messagesPerField.printIfExists('rank','has-error')}">
                            <label for="rank" class="form-label">Pay grade</label>
                            <select id="rank" name="rank" class="form-control">
                                <option selected disabled hidden>Select your rank</option>
                                <optgroup label="Enlisted">
                                    <option>E-1</option>
                                    <option>E-2</option>
                                    <option>E-3</option>
                                    <option>E-4</option>
                                    <option>E-5</option>
                                    <option>E-6</option>
                                    <option>E-7</option>
                                    <option>E-8</option>
                                    <option>E-9</option>
                                </optgroup>
                                <optgroup label="Warrant Officer">
                                    <option>W-1</option>
                                    <option>W-2</option>
                                    <option>W-3</option>
                                    <option>W-4</option>
                                    <option>W-5</option>
                                </optgroup>
                                <optgroup label="Officer">
                                    <option>O-1</option>
                                    <option>O-2</option>
                                    <option>O-3</option>
                                    <option>O-4</option>
                                    <option>O-5</option>
                                    <option>O-6</option>
                                    <option>O-7</option>
                                    <option>O-8</option>
                                    <option>O-9</option>
                                    <option>O-10</option>
                                </optgroup>
                                <optgroup label="Civil Service">
                                    <option>GS-1</option>
                                    <option>GS-2</option>
                                    <option>GS-3</option>
                                    <option>GS-4</option>
                                    <option>GS-5</option>
                                    <option>GS-6</option>
                                    <option>GS-7</option>
                                    <option>GS-8</option>
                                    <option>GS-9</option>
                                    <option>GS-10</option>
                                    <option>GS-11</option>
                                    <option>GS-12</option>
                                    <option>GS-13</option>
                                    <option>GS-14</option>
                                    <option>GS-15</option>
                                    <option>SES</option>
                                </optgroup>
                                <option>N/A</option>
                            </select>
                            <#if messagesPerField.existsError('rank')>
                                <span class="message-details" aria-live="polite">
                                    ${kcSanitize(messagesPerField.get('rank'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>
                    <div class="form-group ${messagesPerField.printIfExists('organization','has-error')}">
                        <label for="organization" class="form-label">Unit, Organization or Company Name</label>
                        <input id="organization" class="form-control" name="organization" type="text"
                            value="${(register.formData['organization']!'')}" autocomplete="company" />
                        <#if messagesPerField.existsError('organization')>
                            <span class="message-details" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('organization'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>
                <div class="location-input">
                    <div class="form-group">
                        <label for="location" class="form-label">Location</label>
                        <input id="location" class="form-control" name="location" tabindex="-1" type="text" />
                    </div>
                </div>
                <#if !realm.registrationEmailAsUsername>
                    <div class="form-group ${messagesPerField.printIfExists('username','has-error')}">
                        <label for="username" class="form-label">
                            ${msg("username")}
                        </label>
                        <input id="username" class="form-control" name="username" type="text"
                            value="${(register.formData.username!'')}" autocomplete="username" />
                        <#if messagesPerField.existsError('username')>
                            <span class="message-details" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('username'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>
                <div class="form-group ${messagesPerField.printIfExists('email','has-error')}">
                    <label for="email" class="form-label">
                        ${msg("email")}
                    </label>
                    <input id="email" class="form-control" name="email" type="text"
                        value="${(register.formData.email!'')}" autocomplete="email" />
                    <#if messagesPerField.existsError('email')>
                        <span class="message-details" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('email'))?no_esc}
                        </span>
                    </#if>
                </div>
                <div class="form-group ${messagesPerField.printIfExists('notes','has-error')}">
                    <label for="notes" class="form-label ">
                        ${msg("accessRequest")}
                    </label>
                    <textarea id="notes" class="form-control " name="notes"></textarea>
                </div>
                <#if properties["USERNAME_PASSWORD_AUTH_ENABLED"] == "true">
                    <div class="form-group ${messagesPerField.printIfExists('password','has-error')}">
                        <#if cacSubjectDN??>
                            <div class="alert alert-info">
                                <div class="row">
                                    <div class="col">
                                        <p>${msg("passwordCacMessage1")}</p>
                                        <p><b>${msg("passwordCacMessage2")}</b></p>
                                        <p>${msg("passwordCacMessage3")}</p>
                                    </div>
                                </div>
                            </div>
                            <label for="password" class="form-label ">
                                ${msg("passwordOptional")}
                            </label>
                        <#else>
                            <label for="password" class="form-label ">
                                ${msg("password")}
                            </label>
                        </#if>
                        <input id="password" class="form-control " name="password"
                            type="password" autocomplete="new-password" />
                        <#if messagesPerField.existsError('password')>
                            <span class="message-details" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('password'))?no_esc}
                            </span>
                        </#if>
                    </div>
                    <div class="form-group ${messagesPerField.printIfExists('password-confirm','has-error')}">
                        <label for="password-confirm" class="form-label ">
                            <#if cacSubjectDN??>
                                ${msg("passwordConfirmOptional")}
                            <#else>
                                ${msg("passwordConfirm")}
                            </#if>
                        </label>
                        <input id="password-confirm" class="form-control " name="password-confirm"
                            type="password" autocomplete="new-password" />
                        <#if messagesPerField.existsError('password-confirm')>
                            <span class="message-details" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>
                <#if recaptchaRequired??>
                    <div class="form-group">
                        <div>
                            <div class="g-recaptcha" data-theme="dark" data-size="normal"
                                data-sitekey="${recaptchaSiteKey}"></div>
                        </div>
                    </div>
                </#if>
                <div class="form-group">
                    <br/>
                    <div id="kc-form-buttons">
                        <input id="do-register" disabled="disabled"
                            class="btn btn-primary btn-block"
                            type="submit" value="${msg("doRegister")}" />
                    </div>
                </div>
            </form>
            <div class="footer-text" id="footer-text">
                You must be a human to register, confidence is increased as you interact with this page.
                <br><br>
                <a>Currently only <span id="confidence">1</span>% convinced you're not a robot.</a>
            </div>
        </#if>
    </@layout.registrationLayout>
    <script>
    if('${properties["ENABLE_REGISTRATION_FIELDS"]}' == "true") {
        document.getElementById('affiliation').value = "${(register.formData['affiliation']!'')}";
        document.getElementById('rank').value = "${(register.formData['rank']!'')}";
    }
    (function() {
        const threshold = 250;
        let count = 0;
        let complete = false;
        window.onload = tracker;
        window.onmousemove = tracker;
        window.onmousedown = tracker;
        window.ontouchstart = tracker;
        window.onclick = tracker;
        window.onkeypress = tracker;
        window.addEventListener('scroll', tracker, true);
        const confidence = document.getElementById('confidence');
        const footer = document.getElementById('footer-text');

        function tracker() {
            if (complete) {
                return;
            }
            count++;
            confidence.innerText = Math.round((count / threshold) * 100);
            if (count > threshold) {
                complete = true;
                const form = document.getElementById('unicorn-registration-form');
                const register = document.getElementById('do-register');
                const location = document.getElementById('location');
                location.value = '42';
            <#if properties["USERNAME_PASSWORD_AUTH_ENABLED"] == "true" || properties["X509_LOGIN_ENABLED"] == "false" || cacSubjectDN??>
                form.setAttribute('action', '${url.registrationAction?no_esc}');
                register.removeAttribute('disabled');
                footer.parentNode.removeChild(footer);
            <#else>
                footer.innerHTML = '<p>Disabled registration due to a missing CAC</p>';
            </#if>

            }
        }
    }());
    </script>
