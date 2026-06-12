package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.AdminOperationLogPage;
import com.lxy.flowplan.pojo.AdminOverview;
import com.lxy.flowplan.pojo.AdminUserRow;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.AdminService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public Result<AdminOverview> getOverview() {
        return Result.success(adminService.getOverview());
    }

    @GetMapping("/users")
    public Result<List<AdminUserRow>> listUsers() {
        return Result.success(adminService.listUsers());
    }

    @GetMapping("/operation-logs")
    public Result<AdminOperationLogPage> listOperationLogs(@RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size,
                                                           @RequestParam(required = false) String module) {
        return Result.success(adminService.listOperationLogs(page, size, module));
    }

    @PatchMapping("/users/{userId}/role")
    public Result<Void> updateUserRole(@PathVariable Integer userId,
                                       @RequestParam String role) {
        adminService.updateUserRole(userId, role);
        return Result.success();
    }

    @DeleteMapping("/users/{userId}")
    public Result<Void> deleteUser(@PathVariable Integer userId) {
        adminService.deleteUser(userId);
        return Result.success();
    }
}
