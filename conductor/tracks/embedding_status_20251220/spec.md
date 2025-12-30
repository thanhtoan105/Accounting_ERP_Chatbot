# Voucher Embedding Status - Phase 1

## Overview

Add an admin endpoint and UI to display voucher embedding progress for the RAG chatbot feature.

## Background

- n8n workflow "Batch Voucher Embedding - Scheduled" (ID: HBDNbdYtopkCNiP5) embeds vouchers into Pinecone
- PostgreSQL `vouchers` table has `embedded_at` column (NULL = not embedded)
- Currently 11,134 unembedded vouchers in the database
- This is Phase 1 of a 3-phase integration plan

## Requirements

### Backend

1. **Endpoint**: `GET /api/admin/vouchers/embedding-status`
2. **Authentication**: Admin role required
3. **Response**: Simple JSON with total and embedded counts
   ```json
   {
     "total": 11134,
     "embedded": 5000,
     "pending": 6134,
     "percentage": 44.92
   }
   ```
4. **SQL Query**: 
   ```sql
   SELECT COUNT(*) AS total, COUNT(embedded_at) AS embedded FROM vouchers
   ```

### Frontend

1. **Location**: Admin dashboard or settings page
2. **Display**: "Embedded: X / Y" with progress bar
3. **Optional**: Percentage display and color coding
4. **No auto-refresh**: Manual page refresh for now

## Acceptance Criteria

- [ ] AC1: GET /api/admin/vouchers/embedding-status returns correct counts
- [ ] AC2: Endpoint requires admin authentication
- [ ] AC3: Frontend displays embedding progress with progress bar
- [ ] AC4: Response includes total, embedded, pending, and percentage fields

## Out of Scope (Future Phases)

- Phase 2: Trigger embedding via POST endpoint
- Phase 3: Auto-polling for live progress updates

## Technical Constraints

- Keep it SIMPLE - no complicated types
- Backend: Spring Boot 3.5 with Java 21
- Frontend: React with TypeScript, shadcn/ui
- Use TanStack Query for data fetching
