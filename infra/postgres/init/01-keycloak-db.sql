-- Creates the auxiliary 'keycloak' database on first boot.
-- Only runs on initial postgres container init (empty data volume).
-- The main 'translately' database is created by POSTGRES_DB in docker-compose.
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
