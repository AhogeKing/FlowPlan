package com.lxy.flowplan.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lxy.flowplan.pojo.Task;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    // Task 列表只按 Project 边界查询；用户边界由调用方先校验 Project 归属。
    default List<Task> selectListByProjectId(Integer projectId) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getDeadline)
                .orderByAsc(Task::getId));
    }

    // 所有单个 Task 操作都要带 projectId，防止跨 Project 更新或删除。
    default Task selectByIdAndProjectId(Integer id, Integer projectId) {
        return selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, id)
                .eq(Task::getProjectId, projectId));
    }

    default boolean existsByIdAndProjectId(Integer id, Integer projectId) {
        return exists(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, id)
                .eq(Task::getProjectId, projectId));
    }

    default int deleteByIdAndProjectId(Integer id, Integer projectId) {
        return delete(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, id)
                .eq(Task::getProjectId, projectId));
    }
}
