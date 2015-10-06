-- sets up the akkapg user and database for tests
-- psql -U <postgres_superuser> -f setup_db.sql

create USER akkapg PASSWORD 'akkapg';
create DATABASE akkapg OWNER akkapg;
\c akkapg
create EXTENSION IF NOT EXISTS hstore;

CREATE SCHEMA "akka-persistence-pg";