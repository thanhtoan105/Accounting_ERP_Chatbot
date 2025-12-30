package com.accounting.dto.embedding;

/**
 * Defines all entity types that can be embedded for RAG chatbot.
 * Each entity type has specific text synthesis and metadata requirements.
 * 
 * <p>Entity types are organized by implementation status:
 * <ul>
 *   <li>IMPLEMENTED - Full embedding support with synthesizer and triggers</li>
 *   <li>PLANNED - Reserved for future implementation</li>
 * </ul>
 */
public enum EntityType {

    // ==================== IMPLEMENTED ====================
    // Master Data (Phase 1 & 3)
    VOUCHER("voucher", "Chứng từ ghi sổ"),
    CHART_OF_ACCOUNTS("coa", "Tài khoản kế toán"),
    CUSTOMER("customer", "Khách hàng"),
    SUPPLIER("supplier", "Nhà cung cấp"),

    // Transactional Documents (Phase 4)
    SALES_INVOICE("sales_invoice", "Hóa đơn bán hàng"),
    PURCHASE_INVOICE("purchase_invoice", "Hóa đơn mua hàng"),
    AR_PAYMENT("ar_payment", "Phiếu thu"),
    AP_PAYMENT("ap_payment", "Phiếu chi"),

    // Regulatory content (Phase 2)
    REGULATORY_TT200("regulatory_tt200", "Thông tư 200"),

    // ==================== PLANNED (Future Phases) ====================
    // Reserved for future implementation - do not remove
    // BANK_ACCOUNT("bank_account", "Tài khoản ngân hàng"),
    // INVENTORY_ITEM("inventory_item", "Hàng hóa/Vật tư"),
    // FIXED_ASSET("fixed_asset", "Tài sản cố định"),
    ;

    private final String code;
    private final String displayNameVi;

    EntityType(String code, String displayNameVi) {
        this.code = code;
        this.displayNameVi = displayNameVi;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayNameVi() {
        return displayNameVi;
    }

    /**
     * Check if this entity type is company-scoped (vs global like regulatory content).
     */
    public boolean isCompanyScoped() {
        return this != REGULATORY_TT200;
    }

    /**
     * Get the Pinecone namespace for this entity type.
     * Company-scoped entities use company_{id}, regulatory uses shared namespace.
     */
    public String getPineconeNamespace(Long companyId) {
        if (isCompanyScoped()) {
            return "company_" + companyId;
        }
        return "regulatory_tt200";
    }
}
