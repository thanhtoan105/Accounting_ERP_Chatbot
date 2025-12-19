package com.accounting.dto.analytics;

import java.util.Set;

import com.accounting.entity.analytics.WidgetType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Widget permissions for current user based on Vietnamese TT200 accounting roles")
public record WidgetPermissionsDTO(
        @Schema(description = "Set of widgets the user can access") Set<WidgetType> accessibleWidgets,
        @Schema(description = "Whether user can trigger manual data refresh") boolean canRefresh,
        @Schema(description = "Whether user can export dashboard data") boolean canExport,
        @Schema(description = "Whether user has full access to all widgets") boolean isFullAccess) {}
