# RAG Chatbot Test Scenarios

Tài liệu này mô tả các kịch bản test cho RAG Chatbot kế toán, bao gồm các loại câu hỏi và kết quả mong đợi.

## Tổng Quan Hệ Thống

### Các Namespace trong Pinecone

| Namespace | Nội dung | Entity Types |
|-----------|----------|--------------|
| `regulatory_tt200` | Thông tư 200/2014/TT-BTC | REGULATORY_TT200 |
| `company-{id}` | Dữ liệu công ty | VOUCHER, CUSTOMER, SUPPLIER, COA, SALES_INVOICE, PURCHASE_INVOICE, AR_PAYMENT, AP_PAYMENT |

### Phân Loại Ý Định (Intent Classification)

| Intent | Mô tả | Namespace tìm kiếm |
|--------|-------|-------------------|
| `REGULATORY` | Câu hỏi về quy định, cách hạch toán | `regulatory_tt200` |
| `TRANSACTION` | Câu hỏi về giao dịch, số dư, chứng từ | `company-{id}` |
| `MASTER_DATA` | Câu hỏi về khách hàng, NCC, tài khoản | `company-{id}` |
| `MIXED` | Kết hợp quy định + dữ liệu công ty | Cả hai |

---

## 1. Câu Hỏi Về Quy Định Kế Toán (REGULATORY)

### 1.1 Tài Khoản Kế Toán

```
Query: "TK 131 dùng để ghi nhận nghiệp vụ gì?"
Expected Intent: REGULATORY
Expected Sources: regulatory_tt200
Expected Answer: Giải thích về TK 131 - Phải thu khách hàng theo TT200
Expected Citations: [Thông tư 200, Điều XX]
```

```
Query: "Kết cấu tài khoản 511 như thế nào?"
Expected Intent: REGULATORY
Expected Sources: regulatory_tt200
Expected Answer: Bên Nợ/Bên Có của TK 511 - Doanh thu bán hàng
```

```
Query: "TK 642 ghi nhận những chi phí nào?"
Expected Intent: REGULATORY
Expected Answer: Chi phí quản lý doanh nghiệp bao gồm...
```

### 1.2 Định Khoản & Bút Toán

```
Query: "Hạch toán khi bán hàng thu tiền mặt như thế nào?"
Expected Intent: REGULATORY
Expected Answer:
- Nợ TK 111 (Tiền mặt)
- Có TK 511 (Doanh thu)
- Có TK 3331 (Thuế GTGT)
```

```
Query: "Bút toán mẫu khi mua hàng trả tiền sau?"
Expected Intent: REGULATORY
Expected Answer:
- Nợ TK 156 (Hàng hóa) / 152 (Nguyên vật liệu)
- Nợ TK 133 (Thuế GTGT được khấu trừ)
- Có TK 331 (Phải trả người bán)
```

```
Query: "Cách ghi sổ khi khách hàng trả tiền?"
Expected Intent: REGULATORY
Expected Answer:
- Nợ TK 111/112 (Tiền)
- Có TK 131 (Phải thu khách hàng)
```

### 1.3 Nguyên Tắc Kế Toán

```
Query: "Nguyên tắc ghi nhận doanh thu theo TT200?"
Expected Intent: REGULATORY
Expected Answer: Doanh thu được ghi nhận khi đáp ứng các điều kiện...
```

```
Query: "Khi nào được ghi nhận chi phí?"
Expected Intent: REGULATORY
Expected Answer: Chi phí được ghi nhận theo nguyên tắc phù hợp...
```

---

## 2. Câu Hỏi Về Giao Dịch (TRANSACTION)

### 2.1 Công Nợ Phải Thu

```
Query: "Công nợ khách hàng ABC là bao nhiêu?"
Expected Intent: TRANSACTION
Expected Sources: company-{id}
Expected Answer: Số tiền còn nợ của KH ABC
Expected Citations: [Chứng từ: PT-2024-XXX, HĐ-2024-XXX]
```

```
Query: "Khách hàng nào còn nợ nhiều nhất?"
Expected Intent: TRANSACTION
Expected Answer: Danh sách top khách hàng theo công nợ
```

```
Query: "Tổng tiền phải thu tháng 12?"
Expected Intent: TRANSACTION
Expected Answer: Tổng số dư TK 131 cuối tháng 12
```

### 2.2 Công Nợ Phải Trả

```
Query: "Còn nợ nhà cung cấp XYZ bao nhiêu?"
Expected Intent: TRANSACTION
Expected Answer: Số tiền còn phải trả NCC XYZ
Expected Citations: [Chứng từ: PC-2024-XXX]
```

```
Query: "Hóa đơn mua hàng nào chưa thanh toán?"
Expected Intent: TRANSACTION
Expected Answer: Danh sách hóa đơn với số dư > 0
```

### 2.3 Phiếu Thu/Chi

```
Query: "Phiếu thu PT-2024-001 có nội dung gì?"
Expected Intent: TRANSACTION
Expected Answer: Chi tiết phiếu thu bao gồm khách hàng, số tiền, ngày...
Expected Citations: [Chứng từ: PT-2024-001]
```

```
Query: "Các phiếu chi trong tuần này?"
Expected Intent: TRANSACTION
Expected Answer: Danh sách phiếu chi với ngày trong tuần
```

```
Query: "Tổng tiền đã thu từ khách hàng ABC?"
Expected Intent: TRANSACTION
Expected Answer: Tổng các phiếu thu của KH ABC
```

### 2.4 Hóa Đơn Bán Hàng

```
Query: "Hóa đơn HĐ-2024-050 bán cho ai?"
Expected Intent: TRANSACTION
Expected Answer: Thông tin hóa đơn bao gồm khách hàng, sản phẩm, số tiền
```

```
Query: "Doanh thu tháng này bao nhiêu?"
Expected Intent: TRANSACTION
Expected Answer: Tổng giá trị hóa đơn bán hàng trong tháng
```

### 2.5 Chứng Từ Ghi Sổ

```
Query: "Chứng từ GS-2024-100 hạch toán như thế nào?"
Expected Intent: TRANSACTION
Expected Answer: Chi tiết bút toán Nợ/Có của chứng từ
Expected Citations: [Chứng từ: GS-2024-100]
```

```
Query: "Các bút toán liên quan TK 111 hôm nay?"
Expected Intent: TRANSACTION
Expected Answer: Danh sách chứng từ có dòng TK 111
```

---

## 3. Câu Hỏi Về Dữ Liệu Danh Mục (MASTER_DATA)

### 3.1 Khách Hàng

```
Query: "Thông tin khách hàng ABC?"
Expected Intent: MASTER_DATA
Expected Answer: Mã KH, tên, MST, địa chỉ, điện thoại, email
```

```
Query: "Khách hàng nào có MST 0123456789?"
Expected Intent: MASTER_DATA
Expected Answer: Thông tin KH có mã số thuế tương ứng
```

```
Query: "Danh sách khách hàng đang hoạt động?"
Expected Intent: MASTER_DATA
Expected Answer: Danh sách KH với trạng thái active
```

### 3.2 Nhà Cung Cấp

```
Query: "Thông tin NCC XYZ?"
Expected Intent: MASTER_DATA
Expected Answer: Mã NCC, tên, MST, địa chỉ, tài khoản ngân hàng
```

```
Query: "Nhà cung cấp nào cung cấp văn phòng phẩm?"
Expected Intent: MASTER_DATA
Expected Answer: Danh sách NCC theo ngành hàng
```

### 3.3 Hệ Thống Tài Khoản

```
Query: "Danh sách tài khoản chi phí?"
Expected Intent: MASTER_DATA
Expected Answer: Các TK nhóm 6xx (chi phí)
```

```
Query: "TK con của tài khoản 111?"
Expected Intent: MASTER_DATA
Expected Answer: TK 1111 (Tiền VND), TK 1112 (Tiền ngoại tệ)...
```

---

## 4. Câu Hỏi Kết Hợp (MIXED)

### 4.1 Quy Định + Dữ Liệu Thực Tế

```
Query: "Công nợ khách hàng ABC và cách hạch toán khi thu tiền?"
Expected Intent: MIXED
Expected Sources: company-{id} + regulatory_tt200
Expected Answer:
1. Công nợ hiện tại của ABC: XXX đồng
2. Khi thu tiền, hạch toán:
   - Nợ TK 111/112
   - Có TK 131
Expected Citations: [Chứng từ: ...], [Thông tư 200]
```

```
Query: "Hóa đơn HĐ-001 và cách ghi sổ doanh thu?"
Expected Intent: MIXED
Expected Answer:
1. Thông tin HĐ-001: Khách hàng X, số tiền Y
2. Bút toán ghi nhận doanh thu theo TT200
```

### 4.2 Kiểm Tra Tuân Thủ

```
Query: "Bút toán GS-100 có đúng theo quy định không?"
Expected Intent: MIXED
Expected Answer:
1. Chi tiết bút toán GS-100
2. So sánh với quy định TT200
3. Đánh giá tuân thủ
```

```
Query: "Các giao dịch TK 131 tháng này có đúng kết cấu không?"
Expected Intent: MIXED
Expected Answer:
1. Liệt kê giao dịch TK 131
2. Kiểm tra Nợ tăng/Có giảm
```

---

## 5. Câu Hỏi Theo Kịch Bản Thực Tế

### 5.1 Kế Toán Viên Mới

```
Query: "Tôi mới vào công ty, TK 131 là gì vậy?"
Expected Intent: REGULATORY
Expected Answer: Giải thích đơn giản về TK 131 - Phải thu khách hàng
```

```
Query: "Khi khách hàng thanh toán thì ghi sổ như thế nào?"
Expected Intent: REGULATORY
Expected Answer: Hướng dẫn step-by-step với bút toán mẫu
```

### 5.2 Kiểm Tra Cuối Tháng

```
Query: "Báo cáo công nợ phải thu cuối tháng 12?"
Expected Intent: TRANSACTION
Expected Answer: Tổng hợp công nợ theo khách hàng
```

```
Query: "Đối chiếu số dư TK 131 với sổ chi tiết?"
Expected Intent: MIXED
Expected Answer: So sánh tổng Nợ-Có TK 131 với chi tiết từng KH
```

### 5.3 Hỗ Trợ Kiểm Toán

```
Query: "Chứng từ nào liên quan đến khoản thanh toán cho NCC ABC?"
Expected Intent: TRANSACTION
Expected Answer: Danh sách PC, hóa đơn mua liên quan NCC ABC
```

```
Query: "Giải thích bút toán số 500 ngày 15/12?"
Expected Intent: MIXED
Expected Answer: Chi tiết bút toán + giải thích nghiệp vụ theo TT200
```

---

## 6. Edge Cases & Error Handling

### 6.1 Không Đủ Thông Tin

```
Query: "Công nợ?"
Expected Response: Yêu cầu cung cấp thêm thông tin (khách hàng nào, khoảng thời gian...)
```

```
Query: "Hạch toán?"
Expected Response: Cần mô tả cụ thể nghiệp vụ cần hạch toán
```

### 6.2 Không Tìm Thấy Dữ Liệu

```
Query: "Công nợ khách hàng NOTEXIST?"
Expected Response: Không tìm thấy khách hàng NOTEXIST trong hệ thống
Suggestions: Kiểm tra lại tên hoặc mã khách hàng
```

### 6.3 Câu Hỏi Ngoài Phạm Vi

```
Query: "Thời tiết hôm nay thế nào?"
Expected Response: Xin lỗi, tôi là trợ lý kế toán và không thể trả lời câu hỏi này.
```

---

## 7. Metrics & Quality Indicators

### 7.1 Confidence Score

| Score | Badge | Ý nghĩa |
|-------|-------|---------|
| >= 0.8 | HIGH | Câu trả lời đáng tin cậy, có nhiều citations |
| 0.5 - 0.8 | MEDIUM | Câu trả lời có thể cần xác nhận thêm |
| < 0.5 | LOW | Không đủ thông tin, cần hỏi thêm |

### 7.2 Response Time

| Loại Query | Target Time |
|------------|-------------|
| Simple (single namespace) | < 2s |
| Mixed (multi namespace) | < 4s |
| Complex (aggregation) | < 6s |

### 7.3 Citation Quality

- Mỗi câu trả lời nên có ít nhất 1 citation
- Citations phải liên quan trực tiếp đến câu hỏi
- Format: `[Thông tư 200]` hoặc `[Chứng từ: PC-2024-001]`

---

## 8. Test Execution Guide

### 8.1 API Endpoint

```bash
POST /api/v1/chatbot/query
Content-Type: application/json
Authorization: Bearer {token}

{
  "query": "Công nợ khách hàng ABC?",
  "company_id": "9",
  "session_id": "test-session-001",
  "language": "vi"
}
```

### 8.2 Expected Response Structure

```json
{
  "answer": "Công nợ khách hàng ABC hiện tại là 15.000.000 đ...",
  "citations": [
    {
      "entityType": "SALES_INVOICE",
      "voucherNumber": "HĐ-2024-050",
      "sourceType": "TRANSACTION",
      "displayName": "Hóa đơn HĐ-2024-050",
      "relevanceScore": 0.85
    }
  ],
  "confidenceScore": 0.82,
  "confidenceBadge": "HIGH",
  "intent": "TRANSACTION",
  "intentConfidence": 0.9,
  "responseTimeMs": 1850
}
```

### 8.3 Automated Testing

```bash
# Run test suite
cd backend
mvnd test -Dtest=ChatbotQueryIntegrationTest

# Test specific scenario
curl -X POST http://localhost:8080/api/v1/chatbot/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"query": "TK 131 là gì?", "company_id": "9"}'
```

---

## 9. Appendix

### A. Từ Khóa Phân Loại Intent

#### REGULATORY Keywords
- thông tư, tt200, quy định, hạch toán, định khoản
- bút toán, kết chuyển, nguyên tắc, phương pháp
- chuẩn mực, chế độ kế toán, tài khoản gì, dùng tk nào

#### TRANSACTION Keywords  
- công nợ, số dư, thanh toán, phiếu thu, phiếu chi
- hóa đơn, doanh thu, chi phí, tháng, quý, năm
- bao nhiêu, tổng cộng, báo cáo, thống kê

#### MASTER_DATA Keywords
- khách hàng, nhà cung cấp, tài khoản, danh sách
- thông tin, mã số thuế, địa chỉ, liên hệ

### B. Account Code Patterns

| Pattern | Mô tả |
|---------|-------|
| TK 1xx | Tài sản ngắn hạn |
| TK 2xx | Tài sản dài hạn |
| TK 3xx | Nợ phải trả |
| TK 4xx | Vốn chủ sở hữu |
| TK 5xx | Doanh thu |
| TK 6xx | Chi phí |
| TK 7xx | Thu nhập khác |
| TK 8xx | Chi phí khác |
| TK 9xx | Xác định kết quả |

---

*Tài liệu này được tạo tự động và cần được cập nhật khi có thay đổi về cấu trúc dữ liệu hoặc logic phân loại intent.*
