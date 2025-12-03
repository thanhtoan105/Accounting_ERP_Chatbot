package com.accounting.dto.audit;

import java.util.List;

/**
 * Paginated response DTO for Cash & Bank audit log queries.
 */
public class CashAuditPageDTO {

  private List<CashAuditLogDTO> data;
  private PageMeta meta;

  public CashAuditPageDTO() {
  }

  public CashAuditPageDTO(List<CashAuditLogDTO> data, PageMeta meta) {
    this.data = data;
    this.meta = meta;
  }

  public List<CashAuditLogDTO> getData() {
    return data;
  }

  public void setData(List<CashAuditLogDTO> data) {
    this.data = data;
  }

  public PageMeta getMeta() {
    return meta;
  }

  public void setMeta(PageMeta meta) {
    this.meta = meta;
  }

  /**
   * Metadata for paginated responses.
   */
  public static class PageMeta {
    private int page;
    private int size;
    private long total;
    private boolean hasNext;
    private boolean hasPrevious;

    public PageMeta() {
    }

    public PageMeta(int page, int size, long total) {
      this.page = page;
      this.size = size;
      this.total = total;
      this.hasNext = (long) (page + 1) * size < total;
      this.hasPrevious = page > 0;
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = page;
    }

    public int getSize() {
      return size;
    }

    public void setSize(int size) {
      this.size = size;
    }

    public long getTotal() {
      return total;
    }

    public void setTotal(long total) {
      this.total = total;
    }

    public boolean isHasNext() {
      return hasNext;
    }

    public void setHasNext(boolean hasNext) {
      this.hasNext = hasNext;
    }

    public boolean isHasPrevious() {
      return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
      this.hasPrevious = hasPrevious;
    }
  }

  /**
   * Create a page response from data and pagination info.
   */
  public static CashAuditPageDTO of(List<CashAuditLogDTO> data, int page, int size, long total) {
    return new CashAuditPageDTO(data, new PageMeta(page, size, total));
  }
}
