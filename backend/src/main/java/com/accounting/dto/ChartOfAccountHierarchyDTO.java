package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for hierarchical Chart of Accounts structure (tree format).
 * Used for API responses that return nested account structures.
 */
public class ChartOfAccountHierarchyDTO {

  private Long id;
  private String code;
  private String name;
  private String type;
  private String normalSide;
  private Boolean postable;
  private Long parentId;
  private Integer orderingPosition;
  private List<ChartOfAccountHierarchyDTO> children;

  public ChartOfAccountHierarchyDTO() {
    this.children = new ArrayList<>();
  }

  public ChartOfAccountHierarchyDTO(
      Long id,
      String code,
      String name,
      String type,
      String normalSide,
      Boolean postable,
      Long parentId,
      Integer orderingPosition) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.type = type;
    this.normalSide = normalSide;
    this.postable = postable;
    this.parentId = parentId;
    this.orderingPosition = orderingPosition;
    this.children = new ArrayList<>();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public Integer getOrderingPosition() {
    return orderingPosition;
  }

  public void setOrderingPosition(Integer orderingPosition) {
    this.orderingPosition = orderingPosition;
  }

  public List<ChartOfAccountHierarchyDTO> getChildren() {
    return children;
  }

  public void setChildren(List<ChartOfAccountHierarchyDTO> children) {
    this.children = children;
  }

  public void addChild(ChartOfAccountHierarchyDTO child) {
    this.children.add(child);
  }
}
