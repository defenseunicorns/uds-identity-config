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
                    <div class="row">
                        <input class="btn btn-primary btn-block"
                               name="accept" id="kc-accept" type="submit" value="${msg("doAccept")}" />
                    </div>
                </div>
            </form>
            <div class="clearfix"></div>
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
