package com.developer.dto;

import java.util.List;

/** Password dropdown for a connection. {@code textPasswordAllowed} is true only on local docker. */
public record ConnectionPasswordOptions(List<VaultEnvOption> options, boolean textPasswordAllowed) {
}
