-- Baseline: the schema as it existed when Flyway was introduced (pre-tags), as
-- Hibernate (MySQLDialect + Spring Boot naming strategy) generates it from the
-- entities. Regenerate for comparison with the SchemaDdlGenerator test
-- (build/baseline-ddl.sql). problem_tags intentionally lives in V2: existing
-- databases get baseline-stamped at V1 and still need V2 to run.
--
-- Existing databases are NOT touched by this file: spring.flyway.baseline-on-
-- migrate marks non-empty schemas as already at version 1. Only a fresh, empty
-- database (new dev DB, CI) runs it.

create table users
(
    id              bigint                 not null auto_increment,
    created_at      datetime(6)            not null,
    discord_user_id varchar(32),
    email           varchar(100)           not null,
    password        varchar(255)           not null,
    role            enum ('ADMIN','USER')  not null,
    updated_at      datetime(6)            not null,
    username        varchar(50)            not null,
    primary key (id)
) engine = InnoDB;

create table problems
(
    id                 bigint                                                 not null auto_increment,
    created_at         datetime(6)                                            not null,
    description        TEXT                                                   not null,
    difficulty         enum ('BRONZE','DIAMOND','GOLD','PLATINUM','SILVER')   not null,
    input_description  TEXT,
    is_public          bit                                                    not null,
    memory_limit       integer                                                not null,
    output_description TEXT,
    time_limit         integer                                                not null,
    title              varchar(200)                                           not null,
    updated_at         datetime(6)                                            not null,
    author_id          bigint                                                 not null,
    primary key (id)
) engine = InnoDB;

create table subtasks
(
    id          bigint       not null auto_increment,
    label       varchar(100) not null,
    order_index integer      not null,
    points      integer      not null,
    problem_id  bigint       not null,
    primary key (id)
) engine = InnoDB;

create table test_cases
(
    id              bigint   not null auto_increment,
    expected_output LONGTEXT not null,
    input           LONGTEXT not null,
    is_sample       bit      not null,
    order_index     integer  not null,
    problem_id      bigint   not null,
    subtask_id      bigint,
    primary key (id)
) engine = InnoDB;

create table submissions
(
    id                   bigint                                                                                                                                 not null auto_increment,
    created_at           datetime(6)                                                                                                                            not null,
    error_message        TEXT,
    is_public            bit                                                                                                                                    not null,
    language             enum ('C','CPP','JAVA','JAVASCRIPT','PYTHON3')                                                                                         not null,
    max_score            integer,
    memory               integer,
    passed_test_cases    integer                                                                                                                                not null,
    runtime              integer,
    score                integer,
    source_code          LONGTEXT                                                                                                                               not null,
    status               enum ('ACCEPTED','COMPILE_ERROR','JUDGING','MEMORY_LIMIT','PARTIAL','PENDING','RUNTIME_ERROR','SYSTEM_ERROR','TIME_LIMIT','WRONG_ANSWER') not null,
    subtask_results_json TEXT,
    total_test_cases     integer                                                                                                                                not null,
    problem_id           bigint                                                                                                                                 not null,
    user_id              bigint                                                                                                                                 not null,
    primary key (id)
) engine = InnoDB;

alter table users
    add constraint UK5xkdkhk21dyxgokycasxr0fya unique (discord_user_id);
alter table users
    add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);
alter table users
    add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

alter table problems
    add constraint FKnivjf1wi1yw4vdoy5tq8x0my0 foreign key (author_id) references users (id);
alter table subtasks
    add constraint FK6hwghfecm6abt1wcicw1jmpv3 foreign key (problem_id) references problems (id);
alter table test_cases
    add constraint FKtddrw6qnmvhxky105cag980j3 foreign key (problem_id) references problems (id);
alter table test_cases
    add constraint FKpw0ay8y3m767nlvo4ex1rniux foreign key (subtask_id) references subtasks (id);
alter table submissions
    add constraint FKj5kbdqokftgx992cx24x3s583 foreign key (problem_id) references problems (id);
alter table submissions
    add constraint FK760bgu69957phd7hax608jdms foreign key (user_id) references users (id);
