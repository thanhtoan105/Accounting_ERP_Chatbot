ALTER TABLE purchase_bills
    ADD COLUMN posted_voucher_id UUID;

ALTER TABLE purchase_bills
    ADD CONSTRAINT fk_purchase_bills_posted_voucher
        FOREIGN KEY (posted_voucher_id) REFERENCES vouchers(id);

CREATE INDEX idx_purchase_bills_posted_voucher_id
    ON purchase_bills(posted_voucher_id);

