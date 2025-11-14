package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Chart of Accounts entity following TT200 standards.
 * Represents a hierarchical account structure with company scoping.
 */
@Entity
@Table(name = "chart_of_accounts")
public class ChartOfAccount implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotBlank
  @Size(min = 1, max = 20)
  @Pattern(regexp = "^\\d{1,4}$", message = "Account code must be numeric (1-4 digits)")
  @Column(name = "code", nullable = false, length = 20)
  private String code;

  @NotBlank
  @Size(max = 255)
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Size(max = 255)
  @Column(name = "name_english", length = 255)
  private String nameEnglish;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @NotNull
  @Column(name = "active", nullable = false)
  private Boolean active = true;

  @NotBlank
  @Column(name = "type", nullable = false, length = 50)
  private String type; // Asset, Liability, Equity, Revenue, Expense

  @NotBlank
  @Column(name = "normal_side", nullable = false, length = 50)
  private String normalSide; // Debit or Credit

  @NotNull
  @Column(name = "postable", nullable = false)
  private Boolean postable;

  @Column(name = "parent_id")
  private Long parentId;

  @ManyToOne
  @JoinColumn(name = "parent_id", insertable = false, updatable = false)
  private ChartOfAccount parent;

  @NotNull
  @Column(name = "ordering_position", nullable = false)
  private Integer orderingPosition;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNameEnglish() {
    return nameEnglish;
  }

  public void setNameEnglish(String nameEnglish) {
    this.nameEnglish = nameEnglish;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getNormalSide() {
    return normalSide;
  }

  public void setNormalSide(String normalSide) {
    this.normalSide = normalSide;
  }

  public Boolean getPostable() {
    return postable;
  }

  public void setPostable(Boolean postable) {
    this.postable = postable;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public ChartOfAccount getParent() {
    return parent;
  }

  public void setParent(ChartOfAccount parent) {
    this.parent = parent;
  }

  public Integer getOrderingPosition() {
    return orderingPosition;
  }

  public void setOrderingPosition(Integer orderingPosition) {
    this.orderingPosition = orderingPosition;
  }
}
