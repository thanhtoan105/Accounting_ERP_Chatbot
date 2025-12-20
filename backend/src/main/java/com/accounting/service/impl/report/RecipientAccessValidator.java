package com.accounting.service.impl.report;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.accounting.entity.User;
import com.accounting.entity.report.RecipientAccessStatus;
import com.accounting.enums.Role;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;

@Component
public class RecipientAccessValidator {

  private final UserRepository userRepository;

  @Value("${reporting.external-emails.allowed:false}")
  private boolean externalEmailsAllowed;

  public RecipientAccessValidator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public RecipientAccessStatus validateRecipientAccess(String recipientEmail, String reportType) {
    Long companyId = CompanyContext.getCompanyId();

    List<User> users =
        userRepository.findByEmailContainingIgnoreCaseAndCompanyId(recipientEmail, companyId);

    Optional<User> userOpt =
        users.stream().filter(u -> u.getEmail().equalsIgnoreCase(recipientEmail)).findFirst();

    if (userOpt.isPresent()) {
      User user = userOpt.get();
      if (hasReportViewPermission(user)) {
        return RecipientAccessStatus.ACCESS_GRANTED;
      } else {
        return RecipientAccessStatus.ACCESS_DENIED;
      }
    }

    if (!externalEmailsAllowed) {
      return RecipientAccessStatus.ACCESS_DENIED;
    }

    return RecipientAccessStatus.ACCESS_GRANTED;
  }

  private boolean hasReportViewPermission(User user) {
    Set<String> allowedRoles =
        Set.of(
            Role.CFO.getValue(),
            Role.CHIEF_ACCOUNTANT.getValue(),
            Role.ADMIN.getValue(),
            Role.ACCOUNTANT.getValue());
    return allowedRoles.contains(user.getRole());
  }

  public Map<String, RecipientAccessStatus> validateAllRecipients(
      List<String> recipients, String reportType) {
    Map<String, RecipientAccessStatus> results = new HashMap<>();
    for (String recipient : recipients) {
      results.put(recipient, validateRecipientAccess(recipient, reportType));
    }
    return results;
  }
}
