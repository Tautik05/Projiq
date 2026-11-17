package projiq.auth.dto;

import java.time.Instant;

public class DevEmailLink {

    private String type; // VERIFICATION, PASSWORD_RESET, INVITATION
    private String recipientEmail;
    private String subject;
    private String link;
    private String rawToken;
    private Instant createdAt;

    public DevEmailLink() {
        this.createdAt = Instant.now();
    }

    public DevEmailLink(String type, String recipientEmail, String subject, String link, String rawToken) {
        this.type = type;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.link = link;
        this.rawToken = rawToken;
        this.createdAt = Instant.now();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getRawToken() {
        return rawToken;
    }

    public void setRawToken(String rawToken) {
        this.rawToken = rawToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
