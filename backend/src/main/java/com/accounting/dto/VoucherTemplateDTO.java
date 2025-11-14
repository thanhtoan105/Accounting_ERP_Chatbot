package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

public class VoucherTemplateDTO extends VoucherTemplateSummaryDTO {

  private List<VoucherTemplateLineDTO> lines = new ArrayList<>();

  public List<VoucherTemplateLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<VoucherTemplateLineDTO> lines) {
    this.lines = lines;
  }
}

