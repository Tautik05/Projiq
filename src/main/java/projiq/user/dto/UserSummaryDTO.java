package projiq.user.dto;

import projiq.user.entity.User;

import java.util.UUID;

public class UserSummaryDTO {

    private UUID id;
    private String email;
    private String fullName;
    private boolean emailVerified;

    public UserSummaryDTO() {
    }

    public UserSummaryDTO(UUID id, String email, String fullName, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.emailVerified = emailVerified;
    }

    public static UserSummaryDTO from(User user) {
        return new UserSummaryDTO(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.isEmailVerified()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
