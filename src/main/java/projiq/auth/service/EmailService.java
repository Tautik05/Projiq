package projiq.auth.service;

import projiq.user.entity.User;

public interface EmailService {

    void sendEmailVerification(User user, String rawToken);

    void sendPasswordReset(User user, String rawToken);

    void sendOrganizationInvitation(String recipientEmail, String organizationName, String inviterName, String rawToken);
}
