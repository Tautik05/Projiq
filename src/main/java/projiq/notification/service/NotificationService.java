package projiq.notification.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.common.exception.ResourceNotFoundException;
import projiq.common.response.PagedResponse;
import projiq.notification.dto.NotificationDTO;
import projiq.notification.entity.Notification;
import projiq.notification.repository.NotificationRepository;
import projiq.organization.entity.Organization;
import projiq.organization.repository.OrganizationRepository;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               OrganizationRepository organizationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NotificationDTO createNotification(UUID userId, UUID organizationId, String title, String message, String type, String resourceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Organization org = organizationId != null ? organizationRepository.findById(organizationId).orElse(null) : null;

        Notification notification = new Notification(user, org, title, message, type, resourceId);
        notification = notificationRepository.save(notification);

        return NotificationDTO.from(notification);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationDTO> getUserNotifications(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
        Page<NotificationDTO> notifPage = notificationRepository.findByUserId(userId, pageable)
                .map(NotificationDTO::from);

        return PagedResponse.from(notifPage);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationDTO markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return NotificationDTO.from(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId, Instant.now());
    }
}
