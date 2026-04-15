-- System SUSPENSE mirrors for common currencies (others created lazily in AccountService)
INSERT INTO accounts (owner_ref, currency, account_type)
VALUES ('SYSTEM_SUSPENSE', 'USD', 'SUSPENSE');

INSERT INTO accounts (owner_ref, currency, account_type)
VALUES ('SYSTEM_SUSPENSE', 'EUR', 'SUSPENSE');

INSERT INTO accounts (owner_ref, currency, account_type)
VALUES ('SYSTEM_SUSPENSE', 'GHS', 'SUSPENSE');
