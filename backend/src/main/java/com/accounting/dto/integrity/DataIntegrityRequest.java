package com.accounting.dto.integrity;

import java.util.List;

public record DataIntegrityRequest(
    List<String> entities,
    boolean throttleBypass) {}
