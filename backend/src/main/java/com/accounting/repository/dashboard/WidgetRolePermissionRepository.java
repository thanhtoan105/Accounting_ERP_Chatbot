package com.accounting.repository.dashboard;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.dashboard.WidgetRolePermission;

@Repository
public interface WidgetRolePermissionRepository extends JpaRepository<WidgetRolePermission, UUID> {

    List<WidgetRolePermission> findByRole(String role);

    List<WidgetRolePermission> findByRoleAndCanViewTrue(String role);

    @Query("SELECT wrp.widgetKey FROM WidgetRolePermission wrp WHERE wrp.role = :role AND wrp.canView = true")
    List<String> findAllowedWidgetKeysByRole(@Param("role") String role);

    boolean existsByWidgetKeyAndRoleAndCanViewTrue(String widgetKey, String role);
}
