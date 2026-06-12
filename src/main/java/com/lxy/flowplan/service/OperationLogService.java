package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.AppUser;

public interface OperationLogService {

    void log(String module, String operationType, String description);

    void logFailureForCurrentRequest(String errorMessage);

    void logLoginSuccess(AppUser user);
}
