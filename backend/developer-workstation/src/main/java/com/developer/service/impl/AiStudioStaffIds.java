package com.developer.service.impl;

import com.developer.entity.User;
import com.developer.exception.AiGenerationException;
import com.developer.repository.UserRepository;

/** Content Organizer 每次调用都要工号（审计到人）；没有就明确失败，不拿别的身份顶替。 */
final class AiStudioStaffIds {

    private AiStudioStaffIds() {
    }

    static String resolve(UserRepository userRepository, String userId) {
        return userRepository.findById(userId)
                .map(User::getEmployeeId)
                .filter(id -> !id.isBlank())
                .map(String::trim)
                .orElseThrow(() -> new AiGenerationException("AI_STUDIO_NO_EMPLOYEE_ID",
                        "Your account has no employee ID, which Content Organizer requires; "
                                + "ask an administrator to set it in Admin Center"));
    }
}
