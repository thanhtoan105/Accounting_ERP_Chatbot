package com.accounting.service;

import java.util.UUID;

import com.accounting.dto.integrity.DataIntegrityJobResponse;
import com.accounting.dto.integrity.DataIntegrityRequest;

import jakarta.servlet.http.HttpServletRequest;

public interface DataIntegrityService {

  DataIntegrityJobResponse triggerScan(DataIntegrityRequest request, HttpServletRequest httpRequest);

  DataIntegrityJobResponse getResults(UUID jobId);
}
