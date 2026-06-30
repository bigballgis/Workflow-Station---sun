package com.developer.dto;

import com.developer.enums.ConnectionType;
import com.developer.enums.EmailConnectionDirection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** SMTP host; defaults from provider preset when omitted on create. */
    private String host;

    /** SMTP port; defaults from preset when omitted. */
    @Min(1)
    @Max(65535)
    private Integer port;

    /** STARTTLS for port 587; defaults to true for custom SMTP when omitted. */
    private Boolean useTls;

    private String fromName;

    private Boolean enabled = true;

    /** OUTBOUND (SMTP send), INBOUND (IMAP monitor), or BOTH. */
    private EmailConnectionDirection direction = EmailConnectionDirection.OUTBOUND;

    /** Optional mailbox address for inbound polling; defaults to the connection email. */
    private String mailboxAddress;
}
