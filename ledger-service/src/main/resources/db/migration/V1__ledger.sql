CREATE TABLE accounts (
 id uuid PRIMARY KEY,owner_id varchar(100) NOT NULL,name varchar(80) NOT NULL,
 currency char(3) NOT NULL CHECK(currency IN ('USD','EUR')),balance numeric(16,2) NOT NULL CHECK(balance>=0),
 version bigint NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX accounts_owner_idx ON accounts(owner_id,created_at DESC);
CREATE TABLE movements (
 id uuid PRIMARY KEY,account_id uuid NOT NULL REFERENCES accounts(id),owner_id varchar(100) NOT NULL,
 amount numeric(16,2) NOT NULL CHECK(amount<>0),balance numeric(16,2) NOT NULL CHECK(balance>=0),
 description varchar(140) NOT NULL,occurred_at timestamptz NOT NULL,sequence bigint NOT NULL,UNIQUE(account_id,sequence));
CREATE INDEX movements_account_idx ON movements(account_id,sequence DESC);
CREATE TABLE outbox (id uuid PRIMARY KEY REFERENCES movements(id),payload text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),published_at timestamptz NULL);
CREATE INDEX outbox_pending_idx ON outbox(created_at) WHERE published_at IS NULL;
