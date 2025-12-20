package com.accounting.dto.reconciliation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result DTO for auto-match operation.
 */
public class AutoMatchResultDTO {

    private int totalLinesProcessed;
    private int matchesFound;
    private int matchesApplied;
    private int noMatchFound;
    private List<MatchSuggestionDTO> suggestions = new ArrayList<>();

    public AutoMatchResultDTO() {
    }

    // Getters and Setters
    public int getTotalLinesProcessed() {
        return totalLinesProcessed;
    }

    public void setTotalLinesProcessed(int totalLinesProcessed) {
        this.totalLinesProcessed = totalLinesProcessed;
    }

    public int getMatchesFound() {
        return matchesFound;
    }

    public void setMatchesFound(int matchesFound) {
        this.matchesFound = matchesFound;
    }

    public int getMatchesApplied() {
        return matchesApplied;
    }

    public void setMatchesApplied(int matchesApplied) {
        this.matchesApplied = matchesApplied;
    }

    public int getNoMatchFound() {
        return noMatchFound;
    }

    public void setNoMatchFound(int noMatchFound) {
        this.noMatchFound = noMatchFound;
    }

    public List<MatchSuggestionDTO> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<MatchSuggestionDTO> suggestions) {
        this.suggestions = suggestions;
    }

    /**
     * Individual match suggestion.
     */
    public static class MatchSuggestionDTO {
        private BankStatementLineDTO statementLine;
        private LedgerTransactionDTO ledgerTransaction;
        private double confidence;
        private String matchReason;

        public BankStatementLineDTO getStatementLine() {
            return statementLine;
        }

        public void setStatementLine(BankStatementLineDTO statementLine) {
            this.statementLine = statementLine;
        }

        public LedgerTransactionDTO getLedgerTransaction() {
            return ledgerTransaction;
        }

        public void setLedgerTransaction(LedgerTransactionDTO ledgerTransaction) {
            this.ledgerTransaction = ledgerTransaction;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getMatchReason() {
            return matchReason;
        }

        public void setMatchReason(String matchReason) {
            this.matchReason = matchReason;
        }
    }
}
