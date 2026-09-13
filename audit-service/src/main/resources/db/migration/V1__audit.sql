CREATE TABLE audit_events(event_id uuid PRIMARY KEY,account_id uuid NOT NULL,owner_id varchar(100) NOT NULL,amount numeric(16,2) NOT NULL,balance numeric(16,2) NOT NULL,sequence bigint NOT NULL,occurred_at timestamptz NOT NULL,received_at timestamptz NOT NULL DEFAULT now(),UNIQUE(account_id,sequence));
CREATE INDEX audit_owner_idx ON audit_events(owner_id,occurred_at DESC,event_id);
