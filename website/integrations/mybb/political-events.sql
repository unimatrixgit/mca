-- Run as the game database owner. Website access remains SELECT-only.
GRANT SELECT (id,yaml) ON dune_politics TO dune_cp;
GRANT SELECT (uuid,faction) ON dune_players TO dune_cp;
CREATE OR REPLACE FUNCTION dune_notify_political_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_notify('dune_political_change','');
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS dune_forum_politics_changed ON dune_politics;
CREATE TRIGGER dune_forum_politics_changed AFTER INSERT OR UPDATE OF yaml ON dune_politics FOR EACH STATEMENT EXECUTE FUNCTION dune_notify_political_change();
DROP TRIGGER IF EXISTS dune_forum_faction_changed ON dune_players;
CREATE TRIGGER dune_forum_faction_changed AFTER INSERT OR UPDATE OF faction OR DELETE ON dune_players FOR EACH STATEMENT EXECUTE FUNCTION dune_notify_political_change();
