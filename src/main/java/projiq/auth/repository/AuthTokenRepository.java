package projiq.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projiq.auth.entity.AuthToken;
import projiq.auth.entity.AuthTokenType;
import projiq.user.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndTokenType(String tokenHash, AuthTokenType tokenType);

    @Modifying
    @Query("UPDATE AuthToken a SET a.used = true WHERE a.user = :user AND a.tokenType = :tokenType AND a.used = false")
    int invalidateExistingTokens(@Param("user") User user, @Param("tokenType") AuthTokenType tokenType);
}
