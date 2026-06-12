package com.lxy.flowplan.repository;

import com.lxy.flowplan.pojo.AiInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiInteractionLogRepository extends JpaRepository<AiInteractionLog, Integer> {
}
