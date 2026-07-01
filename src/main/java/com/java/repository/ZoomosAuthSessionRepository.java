package com.java.repository;

import com.java.model.entity.ZoomosAuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoomosAuthSessionRepository extends JpaRepository<ZoomosAuthSession, Long> {

    Optional<ZoomosAuthSession> findTopByOrderByUpdatedAtDesc();
}
