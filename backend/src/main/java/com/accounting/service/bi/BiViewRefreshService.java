package com.accounting.service.bi;

import com.accounting.dto.bi.BiViewRefreshStatus;

public interface BiViewRefreshService {
    
    void refreshAllViews();
    
    void refreshView(String viewName);
    
    BiViewRefreshStatus getRefreshStatus();
}
