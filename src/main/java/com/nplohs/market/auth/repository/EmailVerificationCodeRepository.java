package com.nplohs.market.auth.repository;

import com.nplohs.market.auth.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findTopByEmailAndUsedFalseOrderByIdDesc(String email);

    @Modifying
    @Query("DELETE FROM EmailVerificationCode e WHERE e.email = :email")
    void deleteByEmail(String email);
}
