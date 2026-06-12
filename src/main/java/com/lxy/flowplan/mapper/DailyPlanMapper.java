package com.lxy.flowplan.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lxy.flowplan.pojo.DailyPlan;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyPlanMapper extends BaseMapper<DailyPlan> {
    default int deleteByProjectIdAndPlanDateGreaterThanEqual(Integer projectId, LocalDate planDate) {
        return delete(Wrappers.<DailyPlan>lambdaQuery()
                .eq(DailyPlan::getProjectId, projectId)
                .ge(DailyPlan::getPlanDate, planDate)
        );
    }

    default List<DailyPlan> selectListByProjectId(Integer projectId) {
        return selectList(new LambdaQueryWrapper<DailyPlan>()
                .eq(DailyPlan::getProjectId, projectId)
                .orderByAsc(DailyPlan::getPlanDate)
                .orderByAsc(DailyPlan::getId)
        );
    }

    default DailyPlan selectByProjectIdAndPlanDate(Integer projectId, LocalDate planDate) {
        return selectOne(new LambdaQueryWrapper<DailyPlan>()
                .eq(DailyPlan::getProjectId, projectId)
                .eq(DailyPlan::getPlanDate, planDate)
        );
    }

    default int deleteByProjectId(Integer projectId) {
        return delete(new LambdaQueryWrapper<DailyPlan>()
                .eq(DailyPlan::getProjectId, projectId));
    }
}
