package com.developer.dto;

import com.developer.enums.ConnectionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailConnectionRequest {

    /** Connection display name; defaults to the mailbox email address */
    @NotBlank
    @Email
    private String name;

    private ConnectionType connectionType = ConnectionType.GMAIL;

    private String username;

    /** Plain password on create/update; omitted when unchanged */
    private String password;

    private String fromName;

    private Boolean enabled = true;
}
