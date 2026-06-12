package com.lxy.flowplan.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lxy.flowplan.pojo.DailyPlanItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DailyPlanItemMapper extends BaseMapper<DailyPlanItem> {
    default List<DailyPlanItem> selectListByPlanId(Integer planId) {
        return selectList(new LambdaQueryWrapper<DailyPlanItem>()
                .eq(DailyPlanItem::getPlanId, planId)
                .orderByAsc(DailyPlanItem::getSortOrder)
        );
    }

    default List<DailyPlanItem> selectListByPlanIds(List<Integer> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<DailyPlanItem>()
                .in(DailyPlanItem::getPlanId, planIds)
                .orderByAsc(DailyPlanItem::getPlanId)
                .orderByAsc(DailyPlanItem::getSortOrder)
        );
    }
}
