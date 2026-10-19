package com.portal.component;

import com.portal.dto.UserSummaryResponse;
import java.util.List;

public interface UserDirectoryComponent {
    List<UserSummaryResponse> summaries(List<String> userIds);
}
