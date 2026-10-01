package com.hire.user.service;
import com.hire.model.dto.*;
import com.hire.user.model.UserRequests;
import java.util.Map;
public interface UserService {
    Map<String,Object> login(UserLoginDTO dto);
    Long register(UserRequests.Register dto);
    Map<String,Object> getById(Long id);
    Map<String,Object> getCurrentUser();
    void updateCurrentUser(UserUpdateDTO dto);
    void updatePassword(PasswordUpdateDTO dto);
    Map<String,Object> listUsers(int page, int size, String role, Integer status, String keyword);
    Map<String,Object> adminDetail(Long id);
    Map<String,Object> updateStatus(Long id, Integer status);
    Map<String,Object> notificationRecipients(long afterId, int size);
    Map<String,Object> statistics(int days);
}
