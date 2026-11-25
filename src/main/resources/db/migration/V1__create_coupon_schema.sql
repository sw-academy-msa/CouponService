-- 쿠폰 서비스 원본입니다. 외부 서비스 식별자는 FK 없이 값으로만 보관합니다.
create table coupon.coupon_definitions (
    id uuid primary key,
    name varchar(60),
    discount_type varchar(16),
    amount bigint,
    rate_bps integer,
    max_discount bigint,
    min_items_amount bigint not null,
    scope varchar(16),
    validity_mode varchar(16),
    valid_from timestamp(6) with time zone,
    valid_until timestamp(6) with time zone,
    valid_minutes integer,
    status varchar(16),
    version bigint not null,
    created_by uuid,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    notice_lead_minutes integer not null
);
create table coupon.coupon_campaigns (
    id uuid primary key,
    code varchar(40),
    definition_id uuid,
    kind varchar(16),
    rule_code varchar(40),
    total_budget integer not null,
    issued_count integer not null,
    per_account_limit integer not null,
    starts_at timestamp(6) with time zone,
    ends_at timestamp(6) with time zone,
    status varchar(16),
    demo boolean not null,
    row_version bigint not null,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone
);
create table coupon.coupon_issues (
    id uuid primary key,
    campaign_id uuid,
    definition_id uuid,
    definition_version bigint not null,
    account_id uuid,
    subject_key varchar(80),
    product_id uuid,
    name varchar(60),
    discount_type varchar(16),
    amount bigint,
    rate_bps integer,
    max_discount bigint,
    min_items_amount bigint not null,
    scope varchar(16),
    status varchar(16),
    valid_from timestamp(6) with time zone,
    valid_until timestamp(6) with time zone,
    source varchar(16),
    source_ref varchar(100),
    used_order_id uuid,
    used_at timestamp(6) with time zone,
    discount_applied bigint,
    expiring_noticed_at timestamp(6) with time zone,
    issued_at timestamp(6) with time zone,
    version bigint not null,
    notice_lead_minutes integer not null
);
create table coupon.coupon_issue_history (
    id uuid primary key,
    issue_id uuid,
    campaign_id uuid,
    account_id uuid,
    action varchar(16),
    order_id uuid,
    actor varchar(40),
    reason varchar(40),
    occurred_at timestamp(6) with time zone
);
create table coupon.coupon_locks (
    order_id uuid primary key,
    issue_id uuid,
    account_id uuid,
    product_id uuid,
    items_amount bigint not null,
    request_hash varchar(64),
    state varchar(16),
    hold_until timestamp(6) with time zone,
    next_check_at timestamp(6) with time zone,
    check_attempts integer not null,
    lease_token uuid,
    lease_until timestamp(6) with time zone,
    finish_reason varchar(40),
    discount_applied bigint,
    locked_at timestamp(6) with time zone,
    finished_at timestamp(6) with time zone,
    version bigint not null
);
create table coupon.coupon_event_receipts (
    event_id uuid primary key,
    topic varchar(120),
    payload_hash varchar(64),
    processed_at timestamp(6) with time zone
);
create table coupon.coupon_pending_targets (
    event_id uuid primary key,
    payload text,
    attempts integer not null,
    next_attempt_at timestamp(6) with time zone,
    lease_token uuid,
    lease_until timestamp(6) with time zone,
    status varchar(16),
    created_at timestamp(6) with time zone
);
alter table coupon.coupon_definitions add constraint ck_definition_discount check ((discount_type='AMOUNT' and amount is not null and amount>0) or (discount_type='RATE' and rate_bps is not null and rate_bps between 1 and 10000 and max_discount is not null and max_discount>0));
alter table coupon.coupon_campaigns add constraint uq_campaign_code unique(code), add constraint fk_campaign_definition foreign key(definition_id) references coupon.coupon_definitions(id), add constraint ck_campaign_budget check(issued_count >= 0 and total_budget >= issued_count), add constraint ck_per_account check(per_account_limit=1);
alter table coupon.coupon_issues add constraint uq_issue_subject unique(campaign_id,account_id,subject_key), add constraint uq_issue_source unique(source,source_ref), add constraint fk_issue_campaign foreign key(campaign_id) references coupon.coupon_campaigns(id);
alter table coupon.coupon_locks add constraint fk_lock_issue foreign key(issue_id) references coupon.coupon_issues(id);
create unique index uq_active_issue_lock on coupon.coupon_locks(issue_id) where state='LOCKED';
create index idx_campaign_rule on coupon.coupon_campaigns(kind,rule_code,status);
create index idx_issue_account on coupon.coupon_issues(account_id,status,valid_until);
create index idx_issue_expiry on coupon.coupon_issues(status,valid_until);
create index idx_issue_notice on coupon.coupon_issues(valid_until) where status='ISSUED' and expiring_noticed_at is null;
create index idx_lock_work on coupon.coupon_locks(state,next_check_at);
create index idx_history_issue on coupon.coupon_issue_history(issue_id,occurred_at);
create index idx_history_campaign on coupon.coupon_issue_history(campaign_id,occurred_at);
create table coupon.outbox_events(id uuid primary key,topic varchar(255) not null,partition_key varchar(255) not null,payload text not null,created_at timestamptz not null,next_attempt_at timestamptz not null,published_at timestamptz,attempts int not null,failed_at timestamptz,last_failure_code varchar(255));
create index idx_outbox_pending on coupon.outbox_events(published_at,next_attempt_at,created_at) where failed_at is null;

alter table coupon.coupon_definitions alter column name set not null, alter column discount_type set not null, alter column min_items_amount set not null, alter column scope set not null, alter column validity_mode set not null, alter column status set not null, alter column created_by set not null, alter column created_at set not null, alter column updated_at set not null;

alter table coupon.coupon_campaigns alter column code set not null, alter column definition_id set not null, alter column kind set not null, alter column starts_at set not null, alter column ends_at set not null, alter column status set not null, alter column created_at set not null, alter column updated_at set not null;

alter table coupon.coupon_issues alter column campaign_id set not null, alter column definition_id set not null, alter column account_id set not null, alter column subject_key set not null, alter column name set not null, alter column discount_type set not null, alter column scope set not null, alter column status set not null, alter column valid_from set not null, alter column valid_until set not null, alter column source set not null, alter column source_ref set not null, alter column issued_at set not null;

alter table coupon.coupon_locks alter column issue_id set not null, alter column account_id set not null, alter column product_id set not null, alter column request_hash set not null, alter column state set not null, alter column hold_until set not null, alter column next_check_at set not null, alter column locked_at set not null;

alter table coupon.coupon_issue_history alter column issue_id set not null, alter column campaign_id set not null, alter column account_id set not null, alter column action set not null, alter column actor set not null, alter column occurred_at set not null;

alter table coupon.coupon_event_receipts alter column topic set not null, alter column payload_hash set not null, alter column processed_at set not null;

alter table coupon.coupon_pending_targets alter column payload set not null, alter column next_attempt_at set not null, alter column status set not null, alter column created_at set not null;
alter table coupon.coupon_definitions add constraint ck_definition_validity check ((validity_mode='FIXED' and valid_from is not null and valid_until is not null and valid_until>valid_from) or (validity_mode='RELATIVE' and valid_minutes is not null and valid_minutes>0)), add constraint ck_definition_min check(min_items_amount>=0 and notice_lead_minutes>0), add constraint ck_definition_scope check(scope in ('ALL','PRODUCT')), add constraint ck_definition_status check(status in ('ACTIVE','RETIRED'));
alter table coupon.coupon_campaigns add constraint ck_campaign_period check(starts_at<ends_at), add constraint ck_campaign_kind check(kind in ('CLAIM','TARGETED','MEMBER')), add constraint ck_campaign_status check(status in ('DRAFT','ACTIVE','PAUSED','ENDED'));
alter table coupon.coupon_issues add constraint ck_issue_period check(valid_from<valid_until),add constraint ck_issue_status check(status in ('ISSUED','LOCKED','USED','EXPIRED','REVOKED')),add constraint ck_issue_product check(scope<>'PRODUCT' or product_id is not null);
alter table coupon.coupon_locks add constraint ck_lock_state check(state in ('LOCKED','USED','RELEASED')),add constraint ck_lock_items check(items_amount>0);
