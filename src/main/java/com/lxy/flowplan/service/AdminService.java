package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.AdminOperationLogPage;
import com.lxy.flowplan.pojo.AdminOverview;
import com.lxy.flowplan.pojo.AdminUserRow;

import java.util.List;

public interface AdminService {

    AdminOverview getOverview();

    List<AdminUserRow> listUsers();

    AdminOperationLogPage listOperationLogs(Integer page, Integer size, String module);

    void updateUserRole(Integer userId, String role);

    void deleteUser(Integer userId);
}
