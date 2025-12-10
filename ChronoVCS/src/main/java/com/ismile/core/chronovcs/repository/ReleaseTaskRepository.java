package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.ReleaseTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReleaseTaskRepository extends JpaRepository<ReleaseTaskEntity, Long> {

    List<ReleaseTaskEntity> findByReleaseId(Long releaseId);
}
