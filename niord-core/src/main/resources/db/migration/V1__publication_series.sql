-- Flyway migration V1: the publication series and issue model.
--
-- GENERATED from the entity model, never hand-written. It is the diff between the
-- schema the entities now produce and the committed baseline, so it cannot drift
-- from them by construction. See schema/README.md to regenerate.
--
-- Deliberately absent: hibernate_sequence. Production already has it, mid-count,
-- and every id in this system is drawn from that one row. Creating it here would
-- reset it.

-- The eight new tables.
create table IssueAuditEntry (id integer not null, issue_id integer, series_id integer, user_id integer, archivePath varchar(512), reason varchar(512), action varchar(255) not null, actorLabel varchar(255), detail TEXT, actorKind enum ('USER','SYSTEM','IMPORT') not null, primary key (id)) engine=InnoDB;
create table IssueMember (id integer not null, issue_id integer not null, message_id integer, override_id integer, sortIndex integer not null, frozenPublishDateFrom datetime(6), frozenPublishDateTo datetime(6), messageUid varchar(36) not null, reasonNote varchar(512), frozenMainType varchar(255) not null, frozenShortId varchar(255), frozenStatus varchar(255) not null, frozenType varchar(255) not null, source enum ('CRITERIA','OVERRIDE_INCLUDE','IMPORTED') not null, primary key (id)) engine=InnoDB;
create table IssueOverride (appliedAtPublish bit, author_id integer not null, id integer not null, issue_id integer not null, message_id integer, version integer, created datetime(6), updated datetime(6), messageUid varchar(36) not null, reason varchar(512) not null, kind enum ('INCLUDE','EXCLUDE') not null, primary key (id)) engine=InnoDB;
create table PublicationIssue (cutoffReconstructed bit not null, id integer not null, memberCount integer not null, publishedBy_id integer, retiredBy_id integer, series_id integer not null, snapshotAliveAtCutoff bit, supersedes_id integer, version integer, week integer, weekTo integer, year integer, created datetime(6), cutoffStampedAt datetime(6), intervalFrom datetime(6), intervalTo datetime(6), publicFrom datetime(6), publicTo datetime(6), publishedAt datetime(6), retiredAt datetime(6), snapshotFrozenAt datetime(6), snapshotIntervalFrom datetime(6), updated datetime(6), snapshotSortBy varchar(32), legacyPublicationId varchar(36), publicId varchar(36) not null, edition varchar(64), repoPath varchar(128) not null, retiredReason varchar(512), criteriaOverride TEXT, criteriaSnapshot TEXT, cutoffSource varchar(255), intervalToSource varchar(255), membershipProvenanceNote TEXT, reportParams TEXT, snapshotDomainId varchar(255), snapshotSeriesIds TEXT, snapshotSortOrder varchar(255), snapshotTimeRelation varchar(255), intervalFromSource enum ('STAMPED','NOMINAL','RECOVERED','MANUAL'), membershipProvenance enum ('EXACT','EXPLAINED_DIFF','UNION_SNAPSHOT','NO_MEMBERSHIP'), publicWindowSource enum ('DERIVED','MANUAL') not null, status enum ('OPEN','PUBLISHED','RETIRED') not null, primary key (id)) engine=InnoDB;
create table PublicationIssueDesc (entity_id integer, fileSize integer, fileSourceSticky bit not null, id integer not null, nameOverridden bit not null, replacedBy_id integer, fileGeneratedAt datetime(6), replacedAt datetime(6), fileHash varchar(64), filePath varchar(512), messageReferenceFormat varchar(512), link varchar(1024), fileName varchar(255), lang varchar(255), name varchar(255) not null, fileSource enum ('GENERATED','UPLOADED'), primary key (id)) engine=InnoDB;
create table PublicationSeries (aliveAtCutoff bit, category_id integer not null, domain_id integer, id integer not null, languageSpecific bit not null, mapThumbnails bit, nominalCutoffDayOfMonth integer, nominalCutoffMonth integer, sortOrder integer, version integer, nominalCutoffTime varchar(5), created datetime(6), firstIssueStartsAt datetime(6), updated datetime(6), messageSortBy varchar(32), legacyTemplateId varchar(36), nominalCutoffTimeZone varchar(64), reportId varchar(64), seriesId varchar(64) not null, criteria TEXT, importSource varchar(255), reportParams TEXT, cadence enum ('NONE','DAILY','WEEKLY','MONTHLY','YEARLY') not null, contentMode enum ('GENERATED_FROM_QUERY','UPLOADED_FILE','EXTERNAL_LINK','NONE') not null, messagePublication enum ('NONE','INTERNAL','EXTERNAL') not null, messageSortOrder enum ('ASC','DESC'), nextIssueCreation enum ('AUTO_ON_PUBLISH','MANUAL') not null, nominalCutoffDay enum ('MONDAY','SUNDAY'), numberingScheme enum ('ISO_WEEK_YEAR','YEAR_EDITION','MONTH_YEAR','EDITION_SEQUENCE','NONE') not null, pageOrientation enum ('PORTRAIT','LANDSCAPE'), pageSize enum ('A3','A4','A5','B4','B5','LETTER','LEGAL','LEDGER'), publicAuthority enum ('LEGACY','NEW') not null, releaseMode enum ('MANUAL_GATE','AUTO_RELEASE') not null, status enum ('DRAFT','ACTIVE','RETIRED') not null, timeRelation enum ('PUBLISHED_IN_INTERVAL','IN_FORCE_AT_CUTOFF'), primary key (id)) engine=InnoDB;
create table PublicationSeries_languages (PublicationSeries_id integer not null, indexNo integer not null, languages varchar(255), primary key (PublicationSeries_id, indexNo)) engine=InnoDB;
create table PublicationSeriesDesc (entity_id integer, id integer not null, messageReferenceFormat varchar(512), linkPattern varchar(1024), fileNamePattern varchar(255), lang varchar(255), name varchar(255) not null, nameSuggestionPattern varchar(255), primary key (id)) engine=InnoDB;

-- Foreign keys and unique constraints, applied after the tables they reference.
alter table PublicationIssue add constraint UK_qv2w5nagw0tw4hl8s5m3ou2j8 unique (legacyPublicationId);
alter table PublicationIssue add constraint UK_corm5ffhmto3gn0mgac4g2odi unique (publicId);
alter table PublicationIssueDesc add constraint UKnxyo2os966rpqsta0c2c512kc unique (lang, entity_id);
alter table PublicationSeries add constraint UK_tdu1yl40x40ici74cle5d7bdq unique (legacyTemplateId);
alter table PublicationSeries add constraint UK_nwx7amsqrrv4ed2t0n5jgpar3 unique (seriesId);
alter table PublicationSeriesDesc add constraint UK7jubvr23fraksfwmxl6s1yskh unique (lang, entity_id);
alter table IssueAuditEntry add constraint FKfivg4crji4febxnx4j7kgk4xy foreign key (issue_id) references PublicationIssue (id);
alter table IssueAuditEntry add constraint FKs20c2flg0umgscmh42qqgsvkr foreign key (series_id) references PublicationSeries (id);
alter table IssueAuditEntry add constraint FKl88ptbjfotuwbimvi07u34716 foreign key (user_id) references User (id);
alter table IssueMember add constraint FK472j8o6bidx0ujtpxb6dogivj foreign key (issue_id) references PublicationIssue (id);
alter table IssueMember add constraint FKc1ewp70i2dhv1g6y68rf1g3yi foreign key (message_id) references Message (id);
alter table IssueMember add constraint FKkxpnlfg6nicwfg6pe3n67m181 foreign key (override_id) references IssueOverride (id);
alter table IssueOverride add constraint FKhs6n3c2s2gthbpjoob4m8cktl foreign key (author_id) references User (id);
alter table IssueOverride add constraint FKegnuo8jf2xiynx9v0n915ko41 foreign key (issue_id) references PublicationIssue (id);
alter table IssueOverride add constraint FKmoxghsfwv6lc0xjp53oalpx9g foreign key (message_id) references Message (id);
alter table PublicationIssue add constraint FKhhoe354bavx4jpn1sj53fnfow foreign key (publishedBy_id) references User (id);
alter table PublicationIssue add constraint FKgpim0injqm0hwere6w2vi1vil foreign key (retiredBy_id) references User (id);
alter table PublicationIssue add constraint FKc8w9sib1ar3frvr9hup1b92nq foreign key (series_id) references PublicationSeries (id);
alter table PublicationIssue add constraint FKqsmdih3mcfuluay8swim6jj19 foreign key (supersedes_id) references PublicationIssue (id);
alter table PublicationIssueDesc add constraint FKdstv6un2ehubb1h4b3kenht5i foreign key (entity_id) references PublicationIssue (id);
alter table PublicationIssueDesc add constraint FKt9cam2tvpdh9aqrog06qrq261 foreign key (replacedBy_id) references User (id);
alter table PublicationSeries add constraint FKmvht1b6ulswiuq5wouepjab3p foreign key (category_id) references PublicationCategory (id);
alter table PublicationSeries add constraint FK544royih0ylbsnnk7xcell66i foreign key (domain_id) references Domain (id);
alter table PublicationSeries_languages add constraint FKkclmuimg8ury8fmgxhgqylmg3 foreign key (PublicationSeries_id) references PublicationSeries (id);
alter table PublicationSeriesDesc add constraint FK9xa9g6tdavk4nksedjn0sxubu foreign key (entity_id) references PublicationSeries (id);

