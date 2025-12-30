# Implementation Plan: Voucher Embedding Status

Track: embedding_status_20251220

## Phase 1: Backend API

### Task 1.1: Create DTO
- [x] Create `EmbeddingStatusResponse` record in `backend/src/main/java/com/accounting/dto/admin/`
- Fields: `total`, `embedded`, `pending`, `percentage`

### Task 1.2: Create Repository Method
- [x] Add `countByCompanyId()` and `countByCompanyIdAndEmbeddedAtIsNotNull()` to `VoucherRepository`

### Task 1.3: Create Service
- [x] Create `EmbeddingServiceImpl` implementing `EmbeddingService` interface
- Query repository and build response DTO

### Task 1.4: Create Controller Endpoint
- [x] Create `AdminVoucherController` in `controller/admin/`
- Endpoint: `GET /api/v1/admin/vouchers/embedding-status`
- Require admin/chief_accountant role via `@PreAuthorize`

### Task 1.5: Backend Tests
- [x] Unit test: `EmbeddingServiceImplTest`
- [x] Integration test: `AdminVoucherControllerIntegrationTest`

## Phase 2: Frontend UI

### Task 2.1: Create API Hook
- [x] Create `useEmbeddingStatus` hook with TanStack Query
- API client: `embeddingApi.ts`

### Task 2.2: Create Progress Component
- [x] Create `EmbeddingStatusCard` component
- Uses shadcn/ui Card, Progress, and Skeleton components
- Display: "Embedded: X / Y" with progress bar and percentage

### Task 2.3: Add to Admin Page
- [x] Added to IntegrityDashboardPage (`/accounting/audit/integrity`)
- Only visible to admin users

### Task 2.4: Frontend Tests
- [x] Component test: `EmbeddingStatusCard.test.tsx`

## Phase 3: Verification

### Task 3.1: Quality Gates
- [x] `mvnd clean compile` passes
- [x] `mvnd spotless:apply` - code formatted
- [x] `npx tsc --noEmit` - no TypeScript errors
- [x] Backend unit tests pass

---

## Progress Notes

### 2024-12-20: Phase 1 Complete
- Created backend endpoint at `/api/v1/admin/vouchers/embedding-status`
- Returns JSON: `{ total, embedded, pending, percentage }`
- Created EmbeddingStatusCard component for frontend
- Added to IntegrityDashboardPage for admin users

### 2024-12-20: Phase 2 Complete - Trigger Batch Embedding
- Added `triggerBatchEmbedding()` method to `EmbeddingService` interface and impl
- Added `POST /api/v1/admin/vouchers/embed/start` endpoint returning 202 Accepted
- Backend proxies webhook call to n8n workflow with secret header
- Added "Start Embedding" button to EmbeddingStatusCard with loading/success/error states
- Added TanStack Query `useStartBatchEmbedding` mutation hook
- Integration tests added, application context loading verified
- Fixed `ChatbotController` to use `@ConditionalOnProperty` for test isolation

### 2024-12-20: Phase 3 Complete - Polling for Progress Updates
- Added `enablePolling` parameter to `useEmbeddingStatus` hook
- When `enablePolling=true`, polls every 30 seconds via TanStack Query `refetchInterval`
- Polling activates after user clicks "Start Embedding" (when `startMutation.isSuccess`)
- TypeScript passes: `npx tsc --noEmit` - no errors
