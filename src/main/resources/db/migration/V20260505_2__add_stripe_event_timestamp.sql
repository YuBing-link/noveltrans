ALTER TABLE stripe_subscription
    ADD COLUMN last_event_created BIGINT DEFAULT NULL COMMENT 'Stripe event created timestamp (epoch seconds), used to prevent out-of-order event overwrites';
