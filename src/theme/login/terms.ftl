<#import "template.ftl" as layout>
    <@layout.registrationLayout displayMessage=false; section>
        <!-- Floating scroll to bottom button -->
        <div id="scrollToBottomBtn">
            <img src="${url.resourcesPath}/img/down-to-bottom.svg" alt="Scroll to bottom">
        </div>
        <#if section="form">
            <div id="kc-terms-text" onclick="javscript:window.scrollTo(0, document.body.scrollHeight);">
                <#if properties["TC_TEXT"]?has_content>
                    ${properties["TC_TEXT"]?no_esc}
                <#else>
                    <div class="col-lg-12">
                        <div class="row">
                            <div class="col-lg-12">
                                <div class="alert alert-warning">
                                    <div class="row">
                                        <div class="col-lg-1 d-flex align-items-start col-alert-icon">
                                            <img src="${url.resourcesPath}/img/icon_warning.svg" />
                                        </div>
                                        <div class="col">
                                            <h3>Update to User Agreement</h3>
                                            <p>Changes have been made to this Information System user agreement. Please read and acknowledge the updated conditions</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="row">
                            <div class="col-lg-12">
                                <h4>You are accessing a U.S. Government (USG) Information System (IS) that is provided for
                                    USG-authorized use only.</h4>
                            </div>
                        </div>

                        <div class="row">
                            <div class="col-lg-12">
                                <h5 class="tc-text">By using this IS (which includes any device attached to this IS), you consent to the following
                                    conditions:</h5>
                            </div>
                        </div>

                        <div class="row tc-text">
                            <div class="col-lg-12">
                                <ul>
                                    <li>The USG routinely intercepts and monitors communications on this IS for purposes including, but
                                        not limited to, penetration testing, COMSEC monitoring, network operations and defense,
                                        personnel misconduct (PM), law enforcement (LE), and counterintelligence (CI) investigations.
                                    </li>
                                    <li>At any time, the USG may inspect and seize data stored on this IS.</li>
                                    <li>Communications using, or data stored on, this IS are not private, are subject to routine
                                        monitoring, interception, and search, and may be disclosed or used for any USG authorized
                                        purpose.
                                    </li>
                                    <li>This IS includes security measures (e.g., authentication and access controls) to protect USG
                                        interests--not for your personal benefit or privacy.
                                    </li>
                                    <li>NOTICE: There is the potential that information presented and exported from the UDS Platform
                                        contains FOUO or Controlled Unclassified Information (CUI). It is the responsibility of all
                                        users to ensure information extracted from UDS Platform is appropriately marked and properly
                                        safeguarded. If you are not sure of the safeguards necessary for the information, contact your
                                        functional lead or Information Security Officer.
                                    </li>
                                    <li>As a user of this IS, you may have access to USG’s UDS Platform. Third-party software publishers
                                        (“Vendors”) provide proprietary software, applications, and/or source code (including any proprietary
                                        data made available through such third-party software) (collectively, “Third-Party Software”) to the USG
                                        solely in order for the USG to harden such Third-Party Software and make such hardened versions of the
                                        Third Party Software available to users of UDS Platform. In the event you use the IS
                                        (including UDS Platform) to access, download, execute, display and/or otherwise use (collectively, “Use”)
                                        such Third-Party Software, by Using the IS (including UDS Platform) and/or such Third-Party Software,
                                        you, on behalf of your organization, hereby agree that all Use of such Third-Party Software is governed by
                                        the Vendor’s end user license agreement (“Vendor EULA”), which may be enforced directly by Vendor and/or USG.
                                        If you do not agree to the entirety of the applicable vendor eula, you are prohibited from using all or any
                                        portion of the third-party software in any manner.
                                    </li>
                                    <li>Notwithstanding the above, using this IS does not constitute consent to PM, LE or CI
                                        investigative searching or monitoring of the content of privileged communications, or work
                                        product, related to personal representation or services by attorneys, psychotherapists, or
                                        clergy, and their assistants. Such communications and work product are private and confidential.
                                    </li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </#if>
            </div>
            <hr>
            <form class="form-actions" action="${url.loginAction}" method="POST">
                <div class="col-lg-12">
                    <div class="row align-items-center">
                        <div class="terms-checkbox-container">
                            <div class="terms-checkbox-wrapper">
                                <input type="checkbox" id="termsCheckbox" class="terms-checkbox">
                                <span class="terms-checkbox-custom"></span>
                                <svg class="terms-checkbox-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M20 6L9 17L4 12" stroke="#3B82F6" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                            </div>
                            <label for="termsCheckbox" class="terms-checkbox-label">
                                I agree to the terms and conditions as set out by the user agreement.
                            </label>
                        </div>
                    </div>
                    <br/>
                    <div class="row">
                        <input class="btn btn-primary btn-block"
                               name="accept" id="kc-accept" type="submit" value="${msg("doAccept")}" />
                    </div>
                </div>
            </form>
            <div class="clearfix"></div>
            <script>
                // Handle checkbox state and styling
                document.addEventListener('DOMContentLoaded', function() {
                    const checkbox = document.getElementById('termsCheckbox');
                    const checkmark = checkbox.nextElementSibling.nextElementSibling;
                    const acceptBtn = document.getElementById('kc-accept');

                    // Initial state
                    updateButtonState();

                    // Toggle checkmark visibility and button state
                    checkbox.addEventListener('change', function() {
                        const svg = this.nextElementSibling.nextElementSibling;
                        if (this.checked) {
                            svg.style.display = 'block';
                        } else {
                            svg.style.display = 'none';
                        }
                        updateButtonState();
                    });

                    // Handle label clicks through event delegation
                    document.addEventListener('click', function(e) {
                        if (e.target.closest('label[for="termsCheckbox"]')) {
                            e.preventDefault();
                            checkbox.checked = !checkbox.checked;
                            const event = new Event('change');
                            checkbox.dispatchEvent(event);
                        }
                    });

                    function updateButtonState() {
                        if (checkbox.checked) {
                            acceptBtn.removeAttribute('disabled');
                            acceptBtn.style.opacity = '1';
                            acceptBtn.style.cursor = 'pointer';
                            acceptBtn.style.backgroundColor = '#1A56DB';
                        } else {
                            acceptBtn.setAttribute('disabled', 'disabled');
                            acceptBtn.style.opacity = '0.5';
                            acceptBtn.style.cursor = 'not-allowed';
                            acceptBtn.style.backgroundColor = '#374151';
                        }
                    }
                });
            </script>
        </#if>
    </@layout.registrationLayout>

    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const scrollBtn = document.getElementById('scrollToBottomBtn');

            function checkScroll() {
                const scrollPosition = window.scrollY + window.innerHeight;
                const pageHeight = document.documentElement.scrollHeight - 20;

                scrollBtn.style.display = (scrollPosition >= pageHeight) ? 'none' : 'flex';
            }

            window.addEventListener('scroll', checkScroll, { passive: true });

            scrollBtn.addEventListener('click', function(e) {
                e.preventDefault();
                window.scrollTo({
                    top: document.documentElement.scrollHeight,
                    behavior: 'smooth'
                });
                scrollBtn.style.display = 'none';
            });

            checkScroll();
        });
    </script>
