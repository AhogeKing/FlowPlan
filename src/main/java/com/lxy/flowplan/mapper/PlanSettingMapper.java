package com.lxy.flowplan.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lxy.flowplan.pojo.PlanSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlanSettingMapper extends BaseMapper<PlanSetting> {
    default PlanSetting findLocalByProjectId(Integer projectId) {
        return selectOne(new LambdaQueryWrapper<PlanSetting>()
                .eq(PlanSetting::getProjectId, projectId)
                .eq(PlanSetting::getScope, "LOCAL"));
    }

    default PlanSetting findGlobalByUserId(Integer userId) {
        return selectOne(new LambdaQueryWrapper<PlanSetting>()
                .eq(PlanSetting::getUserId, userId)
                .eq(PlanSetting::getScope, "GLOBAL"));
    }

    default boolean existsGlobalByUserId(Integer userId) {
        return exists(
                Wrappers.<PlanSetting>lambdaQuery()
                        .eq(PlanSetting::getUserId, userId)
                        .eq(PlanSetting::getScope, "GLOBAL")
        );
    }
}
