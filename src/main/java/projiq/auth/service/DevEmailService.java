package projiq.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import projiq.auth.dto.DevEmailLink;
import projiq.user.entity.User;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
@Primary
public class DevEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DevEmailService.class);
    private static final int MAX_SAVED_LINKS = 100;

    private final String baseUrl;
    private final List<DevEmailLink> devLinks = Collections.synchronizedList(new LinkedList<>());

    public DevEmailService(@Value("${app.frontend.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendEmailVerification(User user, String rawToken) {
        String link = baseUrl + "/verify-email.html?token=" + rawToken;
        String subject = "Verify your Projiq email address";
        log.info("\n============================== [DEV EMAIL] ==============================\n" +
                        "To: {}\nSubject: {}\nAction: Please verify your email by opening the link below:\n{}\n" +
                        "==========================================================================",
                user.getEmail(), subject, link);

        recordLink(new DevEmailLink("EMAIL_VERIFICATION", user.getEmail(), subject, link, rawToken));
    }

    @Override
    public void sendPasswordReset(User user, String rawToken) {
        String link = baseUrl + "/reset-password.html?token=" + rawToken;
        String subject = "Reset your Projiq password";
        log.info("\n============================== [DEV EMAIL] ==============================\n" +
                        "To: {}\nSubject: {}\nAction: Please reset your password by opening the link below:\n{}\n" +
                        "==========================================================================",
                user.getEmail(), subject, link);

        recordLink(new DevEmailLink("PASSWORD_RESET", user.getEmail(), subject, link, rawToken));
    }

    @Override
    public void sendOrganizationInvitation(String recipientEmail, String organizationName, String inviterName, String rawToken) {
        String link = baseUrl + "/accept-invitation.html?token=" + rawToken;
        String subject = String.format("Invitation to join %s on Projiq", organizationName);
        log.info("\n============================== [DEV EMAIL] ==============================\n" +
                        "To: {}\nSubject: {}\nInvited By: {}\nAction: Accept invitation by opening the link below:\n{}\n" +
                        "==========================================================================",
                recipientEmail, subject, inviterName, link);

        recordLink(new DevEmailLink("INVITATION", recipientEmail, subject, link, rawToken));
    }

    private void recordLink(DevEmailLink item) {
        devLinks.add(0, item);
        while (devLinks.size() > MAX_SAVED_LINKS) {
            devLinks.remove(devLinks.size() - 1);
        }
    }

    public List<DevEmailLink> getDevLinks() {
        return List.copyOf(devLinks);
    }

    public void clearDevLinks() {
        devLinks.clear();
    }
}
