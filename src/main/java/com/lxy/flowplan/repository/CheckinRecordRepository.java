package com.lxy.flowplan.repository;

import com.lxy.flowplan.pojo.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Integer> {

    Optional<CheckinRecord> findByPlanItemId(Integer planItemId);

    List<CheckinRecord> findByPlanItemIdIn(Collection<Integer> planItemIds);

    boolean existsByPlanItemId(Integer planItemId);

    long countByCheckinDate(LocalDate checkinDate);

    @Query(value = """
            SELECT cr.*
            FROM checkin_record cr
            JOIN daily_plan_item dpi ON cr.plan_item_id = dpi.id
            JOIN daily_plan dp ON dpi.plan_id = dp.id
            JOIN project p ON dp.project_id = p.id
            WHERE p.user_id = :userId
              AND p.id = :projectId
            ORDER BY cr.checkin_date DESC, cr.created_at DESC, cr.id DESC
            """, nativeQuery = true)
    List<CheckinRecord> findByProjectForUser(@Param("userId") Integer userId,
                                             @Param("projectId") Integer projectId);

    @Query(value = """
            SELECT cr.*
            FROM checkin_record cr
            JOIN daily_plan_item dpi ON cr.plan_item_id = dpi.id
            JOIN daily_plan dp ON dpi.plan_id = dp.id
            JOIN project p ON dp.project_id = p.id
            WHERE p.user_id = :userId
              AND p.id = :projectId
              AND cr.task_id = :taskId
            ORDER BY cr.checkin_date DESC, cr.created_at DESC, cr.id DESC
            """, nativeQuery = true)
    List<CheckinRecord> findByTaskForUser(@Param("userId") Integer userId,
                                          @Param("projectId") Integer projectId,
                                          @Param("taskId") Integer taskId);

    @Query(value = """
            SELECT DISTINCT cr.checkin_date
            FROM checkin_record cr
            JOIN daily_plan_item dpi ON cr.plan_item_id = dpi.id
            JOIN daily_plan dp ON dpi.plan_id = dp.id
            JOIN project p ON dp.project_id = p.id
            WHERE p.user_id = :userId
              AND (:projectId IS NULL OR p.id = :projectId)
              AND cr.checkin_date <= :endDate
            ORDER BY cr.checkin_date DESC
            """, nativeQuery = true)
    List<Date> findDistinctCheckinDatesForAnalytics(@Param("userId") Integer userId,
                                                    @Param("projectId") Integer projectId,
                                                    @Param("endDate") LocalDate endDate);
}
