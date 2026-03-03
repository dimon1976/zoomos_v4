package com.java.repository;

import com.java.model.entity.ZoomosRedmineIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ZoomosRedmineIssueRepository extends JpaRepository<ZoomosRedmineIssue, Long> {

    Optional<ZoomosRedmineIssue> findBySiteName(String siteName);

    List<ZoomosRedmineIssue> findAllBySiteNameIn(Collection<String> siteNames);
}
