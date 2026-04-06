create table if not exists scan_run (
                                        id bigserial primary key,
                                        project_key varchar(200) not null,
    source_type varchar(50) not null,
    source_revision varchar(200),
    status varchar(30) not null,
    is_active boolean not null default false,
    started_at timestamp not null default now(),
    completed_at timestamp,
    metadata_json text
    );

create index if not exists idx_scan_run_project_active
    on scan_run(project_key, is_active);

create index if not exists idx_scan_run_project_status
    on scan_run(project_key, status);

create table if not exists code_entity (
                                           id bigserial primary key,
                                           scan_run_id bigint not null references scan_run(id) on delete cascade,
    entity_key varchar(128) not null,
    entity_type varchar(64) not null,
    package_name text,
    declaring_type text,
    simple_name text,
    qualified_name text not null,
    signature text,
    file_path text,
    summary text,
    content_hash varchar(128),
    is_active boolean not null default true,
    unique (scan_run_id, entity_key)
    );

create index if not exists idx_code_entity_scan_qname
    on code_entity(scan_run_id, qualified_name);

create index if not exists idx_code_entity_scan_declaring_type
    on code_entity(scan_run_id, declaring_type);

create table if not exists call_edge (
                                         id bigserial primary key,
                                         scan_run_id bigint not null references scan_run(id) on delete cascade,
    edge_key varchar(128) not null,
    caller_entity_key varchar(128) not null,
    callee_entity_key varchar(128) not null,
    caller_qname text not null,
    callee_qname text not null,
    file_path text,
    line_number int,
    edge_type varchar(64) not null,
    is_active boolean not null default true,
    unique (scan_run_id, edge_key)
    );

create index if not exists idx_call_edge_caller
    on call_edge(scan_run_id, caller_qname);

create index if not exists idx_call_edge_callee
    on call_edge(scan_run_id, callee_qname);

create index if not exists idx_call_edge_caller_key
    on call_edge(scan_run_id, caller_entity_key);

create index if not exists idx_call_edge_callee_key
    on call_edge(scan_run_id, callee_entity_key);

create table if not exists endpoint_mapping (
                                                id bigserial primary key,
                                                scan_run_id bigint not null references scan_run(id) on delete cascade,
    endpoint_key varchar(128) not null,
    http_method varchar(32),
    class_path text,
    method_path text,
    method_qname text not null,
    file_path text,
    is_active boolean not null default true,
    unique (scan_run_id, endpoint_key)
    );

create index if not exists idx_endpoint_method_qname
    on endpoint_mapping(scan_run_id, method_qname);

create table if not exists touchpoint (
                                          id bigserial primary key,
                                          scan_run_id bigint not null references scan_run(id) on delete cascade,
    touchpoint_key varchar(128) not null,
    category varchar(64) not null,
    caller_qname text not null,
    target_qname text,
    file_path text,
    is_active boolean not null default true,
    unique (scan_run_id, touchpoint_key)
    );

create index if not exists idx_touchpoint_caller
    on touchpoint(scan_run_id, caller_qname);

create index if not exists idx_touchpoint_category
    on touchpoint(scan_run_id, category);

create table if not exists flow_edge (
                                         id bigserial primary key,
                                         scan_run_id bigint not null references scan_run(id) on delete cascade,
    flow_key varchar(128) not null,
    source_method_qname text not null,
    parameter_name text,
    sink_method_qname text not null,
    source_declaring_type text,
    is_active boolean not null default true,
    unique (scan_run_id, flow_key)
    );

create index if not exists idx_flow_edge_source
    on flow_edge(scan_run_id, source_method_qname);

create index if not exists idx_flow_edge_sink
    on flow_edge(scan_run_id, sink_method_qname);

create table if not exists stg_symbols (
                                           scan_run_id bigint not null references scan_run(id) on delete cascade,
    package_name text,
    declaring_type text,
    signature text,
    qualified_name text,
    file_path text
    );

create index if not exists idx_stg_symbols_scan_run
    on stg_symbols(scan_run_id);

create index if not exists idx_stg_symbols_scan_qname
    on stg_symbols(scan_run_id, qualified_name);

create table if not exists stg_call_edges (
                                              scan_run_id bigint not null references scan_run(id) on delete cascade,
    caller_qname text,
    callee_qname text,
    file_path text,
    line_number int
    );

create index if not exists idx_stg_call_edges_scan_run
    on stg_call_edges(scan_run_id);

create index if not exists idx_stg_call_edges_scan_caller
    on stg_call_edges(scan_run_id, caller_qname);

create index if not exists idx_stg_call_edges_scan_callee
    on stg_call_edges(scan_run_id, callee_qname);

create table if not exists stg_api_endpoints (
                                                 scan_run_id bigint not null references scan_run(id) on delete cascade,
    http_method varchar(32),
    class_path text,
    method_path text,
    method_qname text,
    file_path text
    );

create index if not exists idx_stg_api_endpoints_scan_run
    on stg_api_endpoints(scan_run_id);

create table if not exists stg_touchpoints (
                                               scan_run_id bigint not null references scan_run(id) on delete cascade,
    category varchar(64),
    caller_qname text,
    target_qname text,
    file_path text
    );

create index if not exists idx_stg_touchpoints_scan_run
    on stg_touchpoints(scan_run_id);

create index if not exists idx_stg_touchpoints_scan_caller
    on stg_touchpoints(scan_run_id, caller_qname);

create table if not exists stg_flow_edges (
                                              scan_run_id bigint not null references scan_run(id) on delete cascade,
    source_method_qname text,
    parameter_name text,
    sink_method_qname text,
    source_declaring_type text
    );

create index if not exists idx_stg_flow_edges_scan_run
    on stg_flow_edges(scan_run_id);