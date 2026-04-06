create table if not exists semantic_document (
                                                 id bigserial primary key,
                                                 scan_run_id bigint not null references scan_run(id) on delete cascade,
    doc_key varchar(128) not null,
    entity_qualified_name text not null,
    entity_type varchar(64) not null,
    doc_type varchar(64) not null,
    file_path text,
    content text not null,
    content_hash varchar(128),
    is_active boolean not null default true,
    unique (scan_run_id, doc_key)
    );

create index if not exists idx_semantic_document_scan_run
    on semantic_document(scan_run_id);

create index if not exists idx_semantic_document_entity_qname
    on semantic_document(scan_run_id, entity_qualified_name);

create index if not exists idx_semantic_document_doc_type
    on semantic_document(scan_run_id, doc_type);