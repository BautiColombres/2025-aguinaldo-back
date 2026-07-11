package com.medibook.api.repository;

import com.medibook.api.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findByUser_IdAndIsActiveTrue(UUID userId);

    // BBUG-L4: batch active-badge lookup for a set of users (one query instead of one per user).
    List<Badge> findByUser_IdInAndIsActiveTrue(java.util.Collection<UUID> userIds);

    List<Badge> findByUser_IdOrderByEarnedAtDesc(UUID userId);

    Optional<Badge> findByUser_IdAndBadgeType(UUID userId, String badgeType);

    boolean existsByUser_IdAndBadgeType(UUID userId, String badgeType);

    boolean existsByUser_IdAndBadgeTypeAndIsActive(UUID userId, String badgeType, boolean isActive);

    @Query("SELECT COUNT(b) FROM Badge b WHERE b.user.id = :userId AND b.isActive = true")
    long countActiveBadgesByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(b) FROM Badge b WHERE b.user.id = :userId AND b.isActive = true AND b.badgeType != :excludeType")
    long countActiveBadgesByUserIdExcludingType(@Param("userId") UUID userId, @Param("excludeType") String excludeType);

    List<Badge> findByBadgeTypeAndIsActiveTrue(String badgeType);
}