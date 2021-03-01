-- Portfolio
alter table o_pf_page_body add column p_usage int8 default 1;
alter table o_pf_page_body add column p_synthetic_status varchar(32);


-- VFS
alter table o_vfs_metadata rename column fk_author to fk_initialized_by;
alter table o_vfs_revision rename column fk_author to fk_initialized_by;
alter table o_vfs_revision add column fk_lastmodified_by bigint default null;

alter table o_vfs_revision add constraint fvers_modified_by_idx foreign key (fk_lastmodified_by) references o_bs_identity (id);
create index idx_fvers_mod_by_idx on o_vfs_revision (fk_lastmodified_by);

-- Taxonomy linking in portfolio
create table o_pf_page_to_tax_competence (
	id bigserial,
	creationdate timestamp not null,
	fk_tax_competence int8 not null,
	fk_pf_page int8 not null,
	primary key (id)
);

alter table o_pf_page_to_tax_competence add constraint fk_tax_competence_idx foreign key (fk_tax_competence) references o_tax_taxonomy_competence (id);
create index idx_fk_tax_competence_idx on o_pf_page_to_tax_competence (fk_tax_competence);
alter table o_pf_page_to_tax_competence add constraint fk_pf_page_idx foreign key (fk_pf_page) references o_pf_page (id);
create index idx_fk_pf_page_idx on o_pf_page_to_tax_competence (fk_pf_page);


-- Authentication
alter table o_bs_authentication add column issuer varchar(255) default 'DEFAULT' not null;

alter table o_bs_authentication drop constraint o_bs_authentication_provider_authusername_key;
alter table o_bs_authentication add constraint unique_pro_iss_authusername UNIQUE (provider, issuer, authusername);
