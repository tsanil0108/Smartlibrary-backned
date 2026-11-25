package com.smartlibrary.service;

import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.dto.UserDto.UserResponse;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;
import com.smartlibrary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Pageable pageable) {
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        Page<User> page = userRepository.search(pattern, pageable);
        return PageResponse.of(page.map(UserResponse::from).getContent(), page);
    }

    @Transactional
    public UserResponse changeStatus(Long userId, UserStatus status, Long actorId) {
        User user = find(userId);
        if (user.getRole() == Role.ADMIN || user.getId().equals(actorId)) {
            throw new BusinessRuleException("CANNOT_CHANGE_ADMIN", "Admin accounts cannot be suspended here");
        }
        user.setStatus(status);
        auditService.log(actorId, "USER_STATUS_CHANGED", "USER", userId, "status=" + status);
        return UserResponse.from(user);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
