package com.vn.security.core.service.security;

import com.vn.security.core.security.MergedSecurityService;
import com.vn.security.core.security.domain.MenuAppName;
import com.vn.security.core.security.domain.SecMenuPermission;
import com.vn.security.core.security.store.SecMenuPermissionStore;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves app-scoped menu permissions for the current authenticated user.
 * Visibility follows default-deny plus union-of-ALLOW semantics.
 */
@Service
public class CurrentUserMenuPermissionService {

    private final MergedSecurityService mergedSecurityService;
    private final SecMenuPermissionStore secMenuPermissionStore;

    public CurrentUserMenuPermissionService(
        MergedSecurityService mergedSecurityService,
        SecMenuPermissionStore secMenuPermissionStore
    ) {
        this.mergedSecurityService = mergedSecurityService;
        this.secMenuPermissionStore = secMenuPermissionStore;
    }

    public List<String> getAllowedMenuIds(MenuAppName appName) {
        Collection<String> authorityNames = mergedSecurityService.getCurrentUserAuthorityNames();
        if (authorityNames.isEmpty()) {
            return List.of();
        }

        List<SecMenuPermission> grants = secMenuPermissionStore.findAllByAppNameAndRoleIn(appName, authorityNames);
        return grants
            .stream()
            .filter(grant -> "ALLOW".equals(grant.getEffect()))
            .map(SecMenuPermission::getMenuId)
            .distinct()
            .sorted()
            .toList();
    }
}
