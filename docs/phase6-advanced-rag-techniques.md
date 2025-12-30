# Phase 6: Advanced RAG Techniques for Legal/Accounting Domain

## Overview

This document outlines the implementation of 4 advanced RAG techniques to improve accuracy and compliance for the Vietnamese accounting chatbot.

**Status**: 🔄 In Progress  
**Priority**: P1 (Critical for legal compliance)  
**Estimated Effort**: 2-3 days  
**Dependencies**: Phase 5 completed ✅  
**Workflow ID**: `Y3w8Q8Q6vdQIaqFm`

---

## Techniques to Implement

| # | Technique | Purpose | ROI | Complexity |
|---|-----------|---------|-----|------------|
| 1 | Chunk Relevance Filtering | Remove irrelevant chunks before LLM | High | Low |
| 2 | Cross-Encoder Reranking | Improve ranking accuracy | Very High | Medium |
| 3 | Grounding Verification | Prevent hallucination | High | Medium |
| 4 | Citation Verification | Ensure accurate legal citations | High | Medium |

---

## 1. Chunk Relevance Filtering

### Purpose
Filter out low-relevance chunks BEFORE sending to LLM to reduce noise and prevent incorrect associations.

### Implementation

**Node Type**: Code (JavaScript)  
**Position**: After Pinecone retrieval, before Reranking

```javascript
// Chunk Relevance Filtering
const chunks = $input.all().flatMap(item => item.json.matches || []);
const query = $('Query Intent Classifier').first().json.query;
const intent = $('Query Intent Classifier').first().json.intent;

// Configuration
const MIN_SCORE_THRESHOLD = 0.7;  // Minimum similarity score
const MAX_CHUNKS = 10;             // Maximum chunks to keep

// Legal/accounting keywords for boost
const LEGAL_KEYWORDS = [
  'điều', 'khoản', 'thông tư', 'quy định', 'nguyên tắc',
  'hạch toán', 'bút toán', 'tài khoản', 'nợ', 'có'
];

function calculateRelevance(chunk, query, intent) {
  let score = chunk.score || 0;
  const text = (chunk.metadata?.text || '').toLowerCase();
  const queryLower = query.toLowerCase();
  
  // Boost if contains query terms
  const queryTerms = queryLower.split(/\s+/);
  const matchedTerms = queryTerms.filter(term => 
    term.length > 2 && text.includes(term)
  );
  score += matchedTerms.length * 0.05;
  
  // Boost for legal keywords (important for REGULATORY intent)
  if (intent === 'REGULATORY' || intent === 'MIXED') {
    const legalMatches = LEGAL_KEYWORDS.filter(kw => text.includes(kw));
    score += legalMatches.length * 0.03;
  }
  
  // Penalize very short chunks (likely incomplete)
  if (text.length < 50) score -= 0.1;
  
  return Math.min(score, 1.0);
}

// Filter and score chunks
const scoredChunks = chunks.map(chunk => ({
  ...chunk,
  adjustedScore: calculateRelevance(chunk, query, intent)
}));

// Filter by threshold and sort by adjusted score
const filteredChunks = scoredChunks
  .filter(c => c.adjustedScore >= MIN_SCORE_THRESHOLD)
  .sort((a, b) => b.adjustedScore - a.adjustedScore)
  .slice(0, MAX_CHUNKS);

// Track filtering stats
const stats = {
  totalChunks: chunks.length,
  filteredChunks: filteredChunks.length,
  removedChunks: chunks.length - filteredChunks.length,
  avgScore: filteredChunks.length > 0 
    ? (filteredChunks.reduce((a, c) => a + c.adjustedScore, 0) / filteredChunks.length).toFixed(3)
    : 0
};

return [{
  json: {
    filteredChunks: filteredChunks,
    filteringStats: stats,
    query: query,
    intent: intent
  }
}];
```

---

## 2. Cross-Encoder Reranking

### Purpose
Use LLM to rerank chunks based on semantic relevance to query, improving accuracy for legal/accounting terminology.

### Implementation

**Node Type**: Code + HTTP Request (Azure OpenAI)  
**Position**: After Chunk Filtering, before LLM Generation

```javascript
// Prepare reranking prompt
const chunks = $json.filteredChunks;
const query = $json.query;

// Format chunks for reranking
const chunksText = chunks.map((chunk, idx) => 
  `[${idx + 1}] ${chunk.metadata?.text?.substring(0, 500) || ''}`
).join('\n\n');

const rerankPrompt = `Bạn là chuyên gia kế toán Việt Nam. Đánh giá mức độ liên quan của các đoạn văn bản sau với câu hỏi.

CÂU HỎI: ${query}

CÁC ĐOẠN VĂN BẢN:
${chunksText}

Trả về JSON array với format:
[{"index": 1, "relevance": 0.95, "reason": "Trực tiếp giải thích về TK 131"}, ...]

Quy tắc đánh giá:
- 0.9-1.0: Trực tiếp trả lời câu hỏi, có điều khoản/quy định cụ thể
- 0.7-0.9: Liên quan chặt chẽ, cung cấp ngữ cảnh quan trọng
- 0.5-0.7: Liên quan gián tiếp, có thể hữu ích
- < 0.5: Không liên quan hoặc sai ngữ cảnh

CHỈ trả về JSON array, không giải thích thêm.`;

return [{
  json: {
    rerankPrompt: rerankPrompt,
    originalChunks: chunks,
    query: query
  }
}];
```

**After LLM Rerank Response:**

```javascript
// Process reranking results
const rerankResults = JSON.parse($json.choices[0].message.content);
const originalChunks = $('Prepare Rerank').first().json.originalChunks;

// Apply new scores and reorder
const rerankedChunks = rerankResults
  .map(result => {
    const chunk = originalChunks[result.index - 1];
    return {
      ...chunk,
      rerankScore: result.relevance,
      rerankReason: result.reason,
      originalScore: chunk.adjustedScore
    };
  })
  .filter(c => c.rerankScore >= 0.6)  // Keep only relevant
  .sort((a, b) => b.rerankScore - a.rerankScore)
  .slice(0, 8);  // Top 8 for context

return [{
  json: {
    rerankedChunks: rerankedChunks,
    rerankStats: {
      before: originalChunks.length,
      after: rerankedChunks.length,
      avgRerankScore: (rerankedChunks.reduce((a, c) => a + c.rerankScore, 0) / rerankedChunks.length).toFixed(3)
    }
  }
}];
```

---

## 3. Grounding Verification

### Purpose
Verify that the LLM's answer is grounded in the retrieved context. Flag or reject ungrounded claims.

### Implementation

**Node Type**: Code + HTTP Request  
**Position**: After LLM Generation, before Response

```javascript
// Prepare grounding check
const answer = $json.output || $json.text;
const chunks = $('Apply Rerank').first().json.rerankedChunks;

// Extract context text
const contextText = chunks.map(c => c.metadata?.text || '').join('\n---\n');

const groundingPrompt = `Bạn là kiểm toán viên. Kiểm tra xem câu trả lời có được hỗ trợ bởi ngữ cảnh không.

NGỮ CẢNH (từ Thông tư 200 và dữ liệu công ty):
${contextText}

CÂU TRẢ LỜI CẦN KIỂM TRA:
${answer}

Phân tích và trả về JSON:
{
  "isGrounded": true/false,
  "groundingScore": 0.0-1.0,
  "ungroundedClaims": ["claim 1 không có trong ngữ cảnh", ...],
  "supportedClaims": ["claim 1 được hỗ trợ bởi đoạn X", ...],
  "recommendation": "ACCEPT" | "MODIFY" | "REJECT",
  "suggestedModification": "Câu trả lời sửa đổi nếu cần..."
}

Quy tắc:
- Nếu claim về điều/khoản/TK mà KHÔNG có trong ngữ cảnh → ungrounded
- Nếu số liệu cụ thể mà KHÔNG có trong ngữ cảnh → ungrounded
- Giải thích chung về kế toán có thể chấp nhận nếu đúng nguyên tắc`;

return [{
  json: {
    groundingPrompt: groundingPrompt,
    originalAnswer: answer,
    chunks: chunks
  }
}];
```

**Process Grounding Result:**

```javascript
// Apply grounding verification
const groundingResult = JSON.parse($json.choices[0].message.content);
const originalAnswer = $('Prepare Grounding Check').first().json.originalAnswer;

let finalAnswer = originalAnswer;
let groundingApplied = false;

if (groundingResult.recommendation === 'REJECT') {
  // Replace with safe response
  finalAnswer = `Xin lỗi, tôi không có đủ thông tin trong ngữ cảnh để trả lời chính xác câu hỏi này. 

Các điểm không thể xác minh:
${groundingResult.ungroundedClaims.map(c => '- ' + c).join('\n')}

Vui lòng hỏi câu hỏi cụ thể hơn hoặc liên hệ kế toán trưởng.`;
  groundingApplied = true;
} else if (groundingResult.recommendation === 'MODIFY' && groundingResult.suggestedModification) {
  finalAnswer = groundingResult.suggestedModification;
  groundingApplied = true;
}

return [{
  json: {
    answer: finalAnswer,
    groundingResult: {
      isGrounded: groundingResult.isGrounded,
      score: groundingResult.groundingScore,
      recommendation: groundingResult.recommendation,
      ungroundedClaims: groundingResult.ungroundedClaims,
      wasModified: groundingApplied
    },
    originalAnswer: originalAnswer
  }
}];
```

---

## 4. Citation Verification

### Purpose
Ensure all citations in the answer (e.g., "[Thông tư 200, Điều 15]") are accurate and match retrieved chunks.

### Implementation

**Node Type**: Code + HTTP Request  
**Position**: After Grounding Verification, before Final Response

```javascript
// Extract and verify citations
const answer = $json.answer;
const chunks = $('Apply Rerank').first().json.rerankedChunks;

// Extract citations from answer
const citationPatterns = [
  /\[Thông tư 200[^\]]*\]/gi,
  /\[Điều \d+[^\]]*\]/gi,
  /\[Khoản \d+[^\]]*\]/gi,
  /\[TK \d{2,4}[^\]]*\]/gi,
  /\[Chứng từ:[^\]]+\]/gi
];

let citations = [];
citationPatterns.forEach(pattern => {
  const matches = answer.match(pattern) || [];
  citations = citations.concat(matches);
});

// Unique citations
citations = [...new Set(citations)];

// Build context for verification
const contextText = chunks.map(c => c.metadata?.text || '').join('\n---\n');

const verifyPrompt = `Kiểm tra các trích dẫn sau có chính xác theo ngữ cảnh không.

NGỮ CẢNH:
${contextText}

TRÍCH DẪN CẦN KIỂM TRA:
${citations.map((c, i) => `${i + 1}. ${c}`).join('\n')}

CÂU TRẢ LỜI CHỨA TRÍCH DẪN:
${answer}

Trả về JSON:
{
  "verifiedCitations": [
    {"citation": "[TK 131]", "isValid": true, "source": "Đoạn 2 ngữ cảnh"},
    {"citation": "[Điều 15]", "isValid": false, "reason": "Không tìm thấy trong ngữ cảnh"}
  ],
  "allCitationsValid": true/false,
  "invalidCitations": ["[Điều 15]"],
  "suggestedFixes": {"[Điều 15]": "Bỏ hoặc sửa thành [Thông tư 200]"}
}`;

return [{
  json: {
    verifyPrompt: verifyPrompt,
    answer: answer,
    extractedCitations: citations,
    chunks: chunks
  }
}];
```

**Apply Citation Fixes:**

```javascript
// Apply citation verification
const verifyResult = JSON.parse($json.choices[0].message.content);
let answer = $('Prepare Citation Check').first().json.answer;

// Remove or fix invalid citations
if (!verifyResult.allCitationsValid && verifyResult.suggestedFixes) {
  for (const [invalid, fix] of Object.entries(verifyResult.suggestedFixes)) {
    if (fix === 'Bỏ' || fix.toLowerCase().includes('remove')) {
      answer = answer.replace(invalid, '');
    } else {
      answer = answer.replace(invalid, fix);
    }
  }
}

// Build final citations array
const validCitations = verifyResult.verifiedCitations
  .filter(c => c.isValid)
  .map(c => ({
    text: c.citation,
    source: c.source,
    verified: true
  }));

return [{
  json: {
    answer: answer,
    citations: validCitations,
    citationStats: {
      total: verifyResult.verifiedCitations.length,
      valid: validCitations.length,
      invalid: verifyResult.invalidCitations?.length || 0,
      allValid: verifyResult.allCitationsValid
    }
  }
}];
```

---

## Updated Workflow Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         n8n Workflow (Phase 6)                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Webhook → Validate → Extract Params → Intent Classifier                │
│                                              │                          │
│                                              ▼                          │
│                                    ┌─────────────────┐                  │
│                                    │ Pinecone Search │                  │
│                                    │ (Multi-namespace)│                 │
│                                    └────────┬────────┘                  │
│                                              │                          │
│  ┌───────────────────────────────────────────┼──────────────────────┐   │
│  │                    NEW: Advanced RAG Pipeline                    │   │
│  │                                           │                      │   │
│  │                                           ▼                      │   │
│  │                            ┌──────────────────────┐              │   │
│  │                            │ 1. Chunk Relevance   │              │   │
│  │                            │    Filtering         │              │   │
│  │                            └──────────┬───────────┘              │   │
│  │                                       │                          │   │
│  │                                       ▼                          │   │
│  │                            ┌──────────────────────┐              │   │
│  │                            │ 2. Cross-Encoder     │              │   │
│  │                            │    Reranking (LLM)   │              │   │
│  │                            └──────────┬───────────┘              │   │
│  │                                       │                          │   │
│  └───────────────────────────────────────┼──────────────────────────┘   │
│                                          │                              │
│                                          ▼                              │
│                               ┌──────────────────────┐                  │
│                               │ RAG Agent (Generate) │                  │
│                               └──────────┬───────────┘                  │
│                                          │                              │
│  ┌───────────────────────────────────────┼──────────────────────────┐   │
│  │                    NEW: Verification Pipeline                    │   │
│  │                                       │                          │   │
│  │                                       ▼                          │   │
│  │                            ┌──────────────────────┐              │   │
│  │                            │ 3. Grounding         │              │   │
│  │                            │    Verification      │              │   │
│  │                            └──────────┬───────────┘              │   │
│  │                                       │                          │   │
│  │                                       ▼                          │   │
│  │                            ┌──────────────────────┐              │   │
│  │                            │ 4. Citation          │              │   │
│  │                            │    Verification      │              │   │
│  │                            └──────────┬───────────┘              │   │
│  │                                       │                          │   │
│  └───────────────────────────────────────┼──────────────────────────┘   │
│                                          │                              │
│                                          ▼                              │
│                               ┌──────────────────────┐                  │
│                               │   Format Response    │                  │
│                               └──────────────────────┘                  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Notes

### Node Naming Convention
- `Filter Chunks` - Chunk Relevance Filtering
- `Prepare Rerank` - Format for reranking
- `LLM Rerank` - Azure OpenAI call for reranking
- `Apply Rerank` - Process reranking results
- `Prepare Grounding` - Format for grounding check
- `LLM Grounding` - Azure OpenAI call for grounding
- `Apply Grounding` - Process grounding results
- `Prepare Citation Check` - Extract citations
- `LLM Citation Check` - Azure OpenAI call for citation verification
- `Apply Citation Check` - Process citation results

### Performance Considerations
- Total additional LLM calls: 3 (rerank, grounding, citation)
- Estimated latency increase: 3-5 seconds
- Trade-off: Higher accuracy vs longer response time

### Fallback Behavior
- If any verification step fails, use original answer with warning flag
- Log all verification results for audit trail

---

## Success Metrics

| Metric | Before | Target |
|--------|--------|--------|
| Hallucination rate | Unknown | < 5% |
| Citation accuracy | Unknown | > 95% |
| Grounding score | N/A | > 0.85 avg |
| Response relevance | ~70% | > 90% |

---

## Changelog

| Date | Version | Changes |
|------|---------|---------|
| 2024-12-29 | 1.0 | Initial Phase 6 plan |
