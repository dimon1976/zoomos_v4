package com.java.repository;

import com.java.model.entity.ZoomosSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoomosSessionRepository extends JpaRepository<ZoomosSession, Long> {

    Optional<ZoomosSession> findTopByOrderByUpdatedAtDesc();
}
