# Phase 5: Query Routing & Multi-Namespace Search

## Overview

This document outlines the implementation plan for Phase 5 of the RAG chatbot expansion. Phase 5 focuses on enhancing the n8n RAG workflow to support intelligent query routing across multiple Pinecone namespaces.

**Status**: ✅ Implementation Complete  
**Priority**: P2  
**Estimated Effort**: 3-5 days  
**Dependencies**: Phase 1-4 completed ✅  
**Workflow File**: `docs/n8n-workflows/rag-chatbot-query-v3-multi-namespace.json`

---

## Table of Contents

1. [Background](#background)
2. [Architecture](#architecture)
3. [Implementation Tasks](#implementation-tasks)
4. [Intent Classification](#intent-classification)
5. [Namespace Routing](#namespace-routing)
6. [Merge & Rerank Strategy](#merge--rerank-strategy)
7. [n8n Workflow Design](#n8n-workflow-design)
8. [Test Scenarios](#test-scenarios)
9. [Risks & Mitigations](#risks--mitigations)
10. [Success Criteria](#success-criteria)

---

## Background

### Current State (Phase 1-4)

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Unified Embedding Schema (EntityType, EntityEmbeddingPayload) | ✅ Complete |
| 2 | ThongTu200 Regulatory Document Embedding | ✅ Complete |
| 3 | Master Data Embedding (Customer, Supplier, COA) | ✅ Complete |
| 4 | Transaction Embedding (Sales, Purchase, AR/AP Payments) | ✅ Complete |

### Current Limitations

- RAG workflow searches **single namespace only** (`company_{companyId}`)
- No support for **regulatory document queries** (ThongTu200)
- No **intent-based routing** for different query types
- Cannot handle **mixed queries** (e.g., "theo TT200 thì hạch toán khoản này thế nào?")

### Phase 5 Goals

1. **Intelligent Query Routing**: Classify query intent and route to appropriate namespaces
2. **Multi-Namespace Search**: Search `company_{companyId}` AND `regulatory_tt200` in parallel
3. **Result Merging**: Combine, deduplicate, and rerank results from multiple sources
4. **Enhanced Citations**: Support citations from both company data and regulatory documents

---

## Architecture

### Current Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Backend                             │
├─────────────────────────────────────────────────────────────┤
│  ChatbotController                                          │
│       │                                                     │
│       ▼                                                     │
│  ChatbotServiceImpl (validate, audit, save DB)              │
│       │                                                     │
│       ▼                                                     │
│  N8nRAGQueryServiceImpl (call n8n webhook)                  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    n8n Workflow                             │
├─────────────────────────────────────────────────────────────┤
│  Webhook → Pinecone Search → LLM Generate → Response        │
│            (single namespace)                               │
└─────────────────────────────────────────────────────────────┘
```

### Phase 5 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Backend (NO CHANGES)                │
├─────────────────────────────────────────────────────────────┤
│  ChatbotController → ChatbotServiceImpl → N8nRAGQueryService│
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              n8n Workflow (UPDATED)                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Webhook ──► Intent Classifier ──► Namespace Router         │
│              (Rule + AI)               │                    │
│                                        ▼                    │
│                    ┌───────────────────┴───────────────┐    │
│                    │                                   │    │
│                    ▼                                   ▼    │
│           Pinecone Search                 Pinecone Search   │
│           company_{id}                    regulatory_tt200  │
│           (entityType filter)                               │
│                    │                                   │    │
│                    └───────────────┬───────────────────┘    │
│                                    ▼                        │
│                          Normalize Scores                   │
│                                    │                        │
│                                    ▼                        │
│                       Deduplicate + Rerank                  │
│                                    │                        │
│                                    ▼                        │
│                          Cap to 8-12 chunks                 │
│                                    │                        │
│                                    ▼                        │
│                          LLM Generate Answer                │
│                                    │                        │
│                                    ▼                        │
│                          Return Response                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Tasks

### Task Breakdown

| # | Task | Description | Est. Hours |
|---|------|-------------|------------|
| 1 | Design Intent Classifier | Define rules + AI fallback logic | 2h |
| 2 | Create Rule-Based Classifier Node | Code node with Vietnamese keywords | 3h |
| 3 | Create AI Fallback Node | OpenAI call for ambiguous queries | 2h |
| 4 | Create Namespace Router Node | Switch logic based on intent | 2h |
| 5 | Create Parallel Pinecone Search | Multi-namespace search with filters | 3h |
| 6 | Create Merge & Rerank Node | Normalize, dedupe, rerank logic | 4h |
| 7 | Update LLM Prompt | Handle multi-source citations | 2h |
| 8 | Test & Debug | End-to-end testing | 4h |
| 9 | Documentation | Update workflow docs | 2h |

**Total Estimated**: 24 hours (3-4 days)

### Workflow Modification Summary

| Component | Action | Details |
|-----------|--------|---------|
| Webhook Node | Keep | No changes needed |
| **NEW** Intent Classifier | Add | Rule-based + AI fallback |
| **NEW** Switch Node | Add | Route by intent type |
| Pinecone Node | Modify | Add parallel branches + filters |
| **NEW** Merge Node | Add | Combine multi-namespace results |
| **NEW** Rerank Node | Add | Score normalization + sorting |
| LLM Node | Modify | Updated prompt for multi-source |
| Response Node | Modify | Include source namespace in citations |

---

## Intent Classification

### Intent Types

| Intent | Description | Example Queries |
|--------|-------------|-----------------|
| `REGULATORY` | Questions about ThongTu200, accounting rules | "TK 131 dùng cho nghiệp vụ nào?", "Hạch toán giảm giá hàng bán?" |
| `TRANSACTION` | Questions about company transactions | "Công nợ phải trả là bao nhiêu?", "Các phiếu chi tháng này?" |
| `MASTER_DATA` | Questions about customers, suppliers, COA | "Thông tin khách hàng ABC?", "Danh sách tài khoản kế toán?" |
| `MIXED` | Queries needing both regulatory + company data | "Theo TT200 thì hạch toán khoản này thế nào?" |

### Rule-Based Classification (Primary)

```javascript
// Vietnamese keyword patterns
const REGULATORY_KEYWORDS = [
  'thông tư', 'TT200', 'TT 200', 'quy định', 'hạch toán',
  'bút toán', 'định khoản', 'kết chuyển', 'nguyên tắc'
];

const TRANSACTION_KEYWORDS = [
  'công nợ', 'số dư', 'thanh toán', 'phiếu thu', 'phiếu chi',
  'hóa đơn', 'doanh thu', 'chi phí', 'tháng', 'quý', 'năm'
];

const MASTER_DATA_KEYWORDS = [
  'khách hàng', 'nhà cung cấp', 'tài khoản', 'danh sách',
  'thông tin', 'mã số thuế', 'địa chỉ'
];

const ACCOUNT_CODE_PATTERN = /TK\s?\d{2,4}/i;  // TK 131, TK131, TK 6421
```

### Classification Algorithm

```javascript
function classifyIntent(query) {
  const normalizedQuery = query.toLowerCase();
  
  // Count keyword matches
  const scores = {
    REGULATORY: countMatches(normalizedQuery, REGULATORY_KEYWORDS),
    TRANSACTION: countMatches(normalizedQuery, TRANSACTION_KEYWORDS),
    MASTER_DATA: countMatches(normalizedQuery, MASTER_DATA_KEYWORDS)
  };
  
  // Check for account code pattern (strong REGULATORY signal)
  if (ACCOUNT_CODE_PATTERN.test(query)) {
    scores.REGULATORY += 2;
  }
  
  // Determine intent
  const maxScore = Math.max(...Object.values(scores));
  const totalScore = Object.values(scores).reduce((a, b) => a + b, 0);
  
  if (maxScore === 0) {
    return { intent: 'MIXED', confidence: 0.3 };
  }
  
  const confidence = maxScore / totalScore;
  
  // Multiple high scores = MIXED
  const highScores = Object.values(scores).filter(s => s >= maxScore * 0.8);
  if (highScores.length > 1) {
    return { intent: 'MIXED', confidence: 0.5 };
  }
  
  // Return highest scoring intent
  const intent = Object.keys(scores).find(k => scores[k] === maxScore);
  return { intent, confidence };
}
```

### AI Fallback (Secondary)

When rule-based confidence < 0.6, call AI for classification:

```javascript
const AI_CLASSIFICATION_PROMPT = `
Bạn là trợ lý phân loại câu hỏi kế toán. Phân loại câu hỏi sau vào một trong các loại:
- REGULATORY: Câu hỏi về quy định, thông tư 200, cách hạch toán theo luật
- TRANSACTION: Câu hỏi về giao dịch, số liệu công ty (công nợ, doanh thu, chi phí)
- MASTER_DATA: Câu hỏi về thông tin khách hàng, nhà cung cấp, tài khoản kế toán
- MIXED: Câu hỏi kết hợp nhiều loại

Câu hỏi: "{query}"

Trả lời JSON: {"intent": "...", "confidence": 0.0-1.0}
`;
```

---

## Namespace Routing

### Routing Matrix

| Intent | Namespaces | EntityType Filter |
|--------|------------|-------------------|
| `REGULATORY` | `regulatory_tt200` | None |
| `TRANSACTION` | `company_{id}` | `VOUCHER`, `SALES_INVOICE`, `PURCHASE_INVOICE`, `AR_PAYMENT`, `AP_PAYMENT` |
| `MASTER_DATA` | `company_{id}` | `CUSTOMER`, `SUPPLIER`, `CHART_OF_ACCOUNTS` |
| `MIXED` | Both namespaces | All entity types |

### Routing Logic

```javascript
function routeToNamespaces(intent, companyId) {
  const routes = {
    REGULATORY: {
      namespaces: ['regulatory_tt200'],
      filters: {}
    },
    TRANSACTION: {
      namespaces: [`company_${companyId}`],
      filters: {
        entityType: { $in: ['VOUCHER', 'SALES_INVOICE', 'PURCHASE_INVOICE', 'AR_PAYMENT', 'AP_PAYMENT'] }
      }
    },
    MASTER_DATA: {
      namespaces: [`company_${companyId}`],
      filters: {
        entityType: { $in: ['CUSTOMER', 'SUPPLIER', 'CHART_OF_ACCOUNTS'] }
      }
    },
    MIXED: {
      namespaces: [`company_${companyId}`, 'regulatory_tt200'],
      filters: {}  // No entity filter for MIXED
    }
  };
  
  return routes[intent] || routes.MIXED;
}
```

---

## Merge & Rerank Strategy

### Score Normalization

Each Pinecone namespace may return scores on different scales. Normalize before merging:

```javascript
function normalizeScores(results) {
  if (results.length === 0) return [];
  
  const scores = results.map(r => r.score);
  const min = Math.min(...scores);
  const max = Math.max(...scores);
  const range = max - min || 1;
  
  return results.map(r => ({
    ...r,
    normalizedScore: (r.score - min) / range
  }));
}
```

### Deduplication

Remove duplicates based on `entityId + entityType`:

```javascript
function deduplicateResults(results) {
  const seen = new Set();
  return results.filter(r => {
    const key = `${r.entityType}_${r.entityId}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
```

### Source-Aware Weighting

Apply weights based on query intent and source:

```javascript
function applySourceWeights(results, intent) {
  const weights = {
    REGULATORY: { regulatory_tt200: 1.2, company: 0.8 },
    TRANSACTION: { regulatory_tt200: 0.7, company: 1.3 },
    MASTER_DATA: { regulatory_tt200: 0.6, company: 1.4 },
    MIXED: { regulatory_tt200: 1.0, company: 1.0 }
  };
  
  const w = weights[intent] || weights.MIXED;
  
  return results.map(r => ({
    ...r,
    weightedScore: r.normalizedScore * (r.namespace.includes('regulatory') ? w.regulatory_tt200 : w.company)
  }));
}
```

### Final Ranking

```javascript
function rankAndCap(results, maxResults = 10) {
  return results
    .sort((a, b) => b.weightedScore - a.weightedScore)
    .slice(0, maxResults);
}
```

---

## n8n Workflow Design

### Node-by-Node Specification

#### Node 1: Webhook (Existing)

- **Type**: Webhook
- **Method**: POST
- **Path**: `/rag-query`
- **Input**: `{ query, companyId, userId, sessionId, language }`

#### Node 2: Intent Classifier (NEW)

- **Type**: Code
- **Function**: Rule-based intent classification
- **Output**: `{ intent, confidence, extractedEntities }`

```javascript
// n8n Code Node
const query = $input.first().json.query;

// Rule-based classification logic here...

return [{
  json: {
    ...$input.first().json,
    intent: classificationResult.intent,
    intentConfidence: classificationResult.confidence,
    extractedEntities: extractedEntities
  }
}];
```

#### Node 3: AI Fallback (NEW, Conditional)

- **Type**: HTTP Request (OpenAI)
- **Condition**: `intentConfidence < 0.6`
- **Purpose**: Use AI for ambiguous queries

#### Node 4: Namespace Router (NEW)

- **Type**: Switch
- **Branches**:
  - REGULATORY → Pinecone (regulatory_tt200)
  - TRANSACTION → Pinecone (company with filter)
  - MASTER_DATA → Pinecone (company with filter)
  - MIXED → Parallel Pinecone (both namespaces)

#### Node 5a: Pinecone - Company Namespace

- **Type**: HTTP Request
- **URL**: Pinecone query endpoint
- **Body**:

```json
{
  "namespace": "company_{{ $json.companyId }}",
  "topK": 10,
  "vector": "{{ $json.queryEmbedding }}",
  "filter": {
    "entityType": { "$in": ["VOUCHER", "SALES_INVOICE", ...] }
  },
  "includeMetadata": true
}
```

#### Node 5b: Pinecone - Regulatory Namespace

- **Type**: HTTP Request
- **URL**: Pinecone query endpoint
- **Body**:

```json
{
  "namespace": "regulatory_tt200",
  "topK": 10,
  "vector": "{{ $json.queryEmbedding }}",
  "includeMetadata": true
}
```

#### Node 6: Merge Results (NEW)

- **Type**: Merge
- **Mode**: Combine
- **Purpose**: Collect results from all Pinecone branches

#### Node 7: Normalize & Rerank (NEW)

- **Type**: Code
- **Function**: Apply normalization, dedup, weighting, ranking

```javascript
const allResults = $input.all().flatMap(item => item.json.matches || []);
const intent = $('Intent Classifier').first().json.intent;

// Normalize scores
const normalized = normalizeScores(allResults);

// Deduplicate
const deduped = deduplicateResults(normalized);

// Apply weights
const weighted = applySourceWeights(deduped, intent);

// Rank and cap
const final = rankAndCap(weighted, 10);

return [{ json: { rankedResults: final } }];
```

#### Node 8: LLM Generate Answer (Existing, Modified)

- **Type**: HTTP Request (Azure OpenAI)
- **Prompt**: Updated to handle multi-source citations

```
Bạn là trợ lý kế toán AI. Trả lời câu hỏi dựa trên ngữ cảnh được cung cấp.

NGUỒN DỮ LIỆU:
{{ rankedResults }}

CÂU HỎI: {{ query }}

Hướng dẫn:
1. Trả lời bằng tiếng Việt
2. Trích dẫn nguồn: [Thông tư 200] hoặc [Chứng từ: XXX]
3. Nếu không có đủ thông tin, nói rõ ràng
4. Phân biệt rõ giữa quy định pháp lý và dữ liệu công ty
```

#### Node 9: Format Response (Existing, Modified)

- **Type**: Code
- **Function**: Format citations with source namespace

---

## Test Scenarios

### Test Cases

| # | Query | Expected Intent | Expected Namespaces | Expected Behavior |
|---|-------|-----------------|---------------------|-------------------|
| 1 | "TK 131 dùng cho nghiệp vụ nào?" | REGULATORY | regulatory_tt200 | Return TT200 account definition |
| 2 | "Công nợ phải trả là bao nhiêu?" | TRANSACTION | company_{id} | Return AP balance from vouchers |
| 3 | "Thông tin khách hàng ABC?" | MASTER_DATA | company_{id} | Return customer details |
| 4 | "Theo TT200 thì hạch toán khoản này thế nào?" | MIXED | Both | Return both regulation + examples |
| 5 | "Hạch toán giảm giá hàng bán?" | REGULATORY | regulatory_tt200 | Return TT200 guidance |
| 6 | "Các hóa đơn bán hàng tháng 11?" | TRANSACTION | company_{id} | Return sales invoices |
| 7 | "Nhà cung cấp nào có công nợ lớn nhất?" | MIXED | company_{id} | Return supplier + AP data |

### Validation Checklist

- [ ] Rule-based classifier correctly identifies intent
- [ ] AI fallback triggers for ambiguous queries
- [ ] Parallel Pinecone searches complete successfully
- [ ] Results are properly merged and deduplicated
- [ ] Score normalization produces fair rankings
- [ ] Citations include source namespace
- [ ] LLM generates coherent multi-source answers
- [ ] Response time < 5 seconds for typical queries

---

## Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Intent misclassification | Medium | Medium | AI fallback + confidence threshold |
| Pinecone timeout on parallel queries | High | Low | Implement timeout handling + retry |
| Score normalization edge cases | Low | Medium | Handle empty results gracefully |
| LLM confusion with multi-source context | Medium | Medium | Clear prompt formatting |
| Workflow complexity increases debugging | Medium | High | Add logging nodes at each step |
| Security: cross-company data leak | Critical | Low | Always filter by companyId |

### Rollback Plan

If Phase 5 causes issues:
1. Disable new nodes in n8n workflow
2. Revert to single-namespace search
3. Keep old workflow as backup

---

## Success Criteria

### Functional Requirements

- [ ] Chatbot correctly answers regulatory questions from ThongTu200
- [ ] Chatbot correctly answers transaction questions from company data
- [ ] Chatbot handles mixed queries with both sources
- [ ] Citations clearly indicate source (regulatory vs company)

### Performance Requirements

- [ ] Intent classification < 100ms
- [ ] Total response time < 5 seconds (p95)
- [ ] Parallel Pinecone queries complete within 2 seconds

### Quality Requirements

- [ ] Intent classification accuracy > 85%
- [ ] User satisfaction maintained or improved
- [ ] No regression in existing query types

---

## Appendix

### A. Vietnamese Keyword Dictionary

```javascript
const VIETNAMESE_ACCOUNTING_TERMS = {
  // Regulatory
  thong_tu: ['thông tư', 'TT200', 'TT 200', 'thông tư 200'],
  hach_toan: ['hạch toán', 'định khoản', 'bút toán', 'ghi sổ'],
  quy_dinh: ['quy định', 'nguyên tắc', 'điều', 'khoản'],
  
  // Transactions
  cong_no: ['công nợ', 'phải thu', 'phải trả', 'nợ'],
  thanh_toan: ['thanh toán', 'trả tiền', 'thu tiền', 'chi tiền'],
  hoa_don: ['hóa đơn', 'chứng từ', 'phiếu', 'bill'],
  
  // Master Data
  khach_hang: ['khách hàng', 'KH', 'customer'],
  nha_cung_cap: ['nhà cung cấp', 'NCC', 'vendor', 'supplier'],
  tai_khoan: ['tài khoản', 'TK', 'account', 'COA']
};
```

### B. EntityType Reference

| EntityType | Description | Namespace |
|------------|-------------|-----------|
| VOUCHER | General journal entries | company_{id} |
| CUSTOMER | Customer master data | company_{id} |
| SUPPLIER | Supplier master data | company_{id} |
| CHART_OF_ACCOUNTS | Chart of accounts | company_{id} |
| SALES_INVOICE | Sales invoices | company_{id} |
| PURCHASE_INVOICE | Purchase bills | company_{id} |
| AR_PAYMENT | Accounts receivable payments | company_{id} |
| AP_PAYMENT | Accounts payable payments | company_{id} |
| REGULATORY_TT200 | ThongTu200 regulations | regulatory_tt200 |

### C. Pinecone Query Examples

**Company namespace with entityType filter:**

```json
{
  "namespace": "company_123",
  "topK": 10,
  "vector": [0.1, 0.2, ...],
  "filter": {
    "entityType": { "$in": ["VOUCHER", "AR_PAYMENT", "AP_PAYMENT"] }
  },
  "includeMetadata": true
}
```

**Regulatory namespace:**

```json
{
  "namespace": "regulatory_tt200",
  "topK": 10,
  "vector": [0.1, 0.2, ...],
  "includeMetadata": true
}
```

---

## Changelog

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2024-12-29 | 1.0 | AI Assistant | Initial plan document |
