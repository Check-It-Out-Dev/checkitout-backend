--liquibase formatted sql
--changeset owasp-p2:add-unique-influencer-opportunity
ALTER TABLE applied_opportunity ADD CONSTRAINT uk_influencer_opportunity UNIQUE (influencer_id, partnership_opportunity_id);
