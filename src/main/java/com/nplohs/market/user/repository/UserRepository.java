package com.nplohs.market.user.repository;

import com.nplohs.market.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    
    java.util.List<User> findByActiveFalseAndDeletedAtBefore(java.time.LocalDateTime time);

    @Query("SELECT u.id FROM User u WHERE u.profileImage LIKE CONCAT('%', :key)")
    Optional<Long> findIdByProfileImageKey(@Param("key") String key);
}
