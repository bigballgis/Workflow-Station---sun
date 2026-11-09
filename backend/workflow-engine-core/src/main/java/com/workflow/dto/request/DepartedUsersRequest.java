package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Admin Center's notice that these users were set INACTIVE or deleted (platform access model D-42). */
public record DepartedUsersRequest(@NotEmpty @Size(max = 1000) List<@NotBlank @Size(max = 64) String> userIds) {
}
