create table `budget`
(
    id              int auto_increment
        primary key,
    remaining       decimal(13, 2)                     not null,
    created_at      datetime default CURRENT_TIMESTAMP not null,
    updated_at      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
);

create table reserve
(
    id         char(36)                           not null
        primary key,
    budget_id  int                                not null,
    coupon_id  int                                not null,
    user_id    int                                not null,
    amount     decimal(13, 2)                     not null,
    status     varchar(30)                        not null,
    expire_at  datetime                           not null,
    created_at datetime default CURRENT_TIMESTAMP not null,
    updated_at datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
);
