package com.lxy.flowplan.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lxy.flowplan.pojo.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    // Project 的所有读取都要带 userId，避免跨用户访问数据。
    default Project selectByIdAndUserId(Integer projectId, Integer userId) {
        return selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getUserId, userId)
        );
    }

    // 新增和改名时复用同一段查重逻辑；excludeProjectId 用于更新时排除自己。
    default boolean existsByNameAndUserId(String name, Integer userId, Integer excludeProjectId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .eq(Project::getName, name);
        if (excludeProjectId != null) {
            wrapper.ne(Project::getId, excludeProjectId);
        }
        return exists(wrapper);
    }

    // 给其它模块做轻量归属校验，避免重复写 id + userId 条件。
    default boolean existsByIdAndUserId(Integer projectId, Integer userId) {
        return exists(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getId, projectId)
                        .eq(Project::getUserId, userId));
    }

    default int deleteByIdAndUserId(Integer projectId, Integer userId) {
        return delete(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getUserId, userId));
    }
}
