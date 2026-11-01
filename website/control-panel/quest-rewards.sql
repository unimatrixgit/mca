-- Run as the PostgreSQL administrator in the existing dunerealms database.
-- No production discovery or credit is created by this migration.
BEGIN;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='dune_quest_owner') THEN
  CREATE ROLE dune_quest_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
 END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS dune_quest_private AUTHORIZATION dune_quest_owner;
REVOKE ALL ON SCHEMA dune_quest_private FROM PUBLIC;
CREATE TABLE IF NOT EXISTS dune_quest_private.first_finder (
 quest_id TEXT PRIMARY KEY CHECK (quest_id='imperial-portal-eye-ra-v1'),
 account_id UUID NOT NULL,
 minecraft_uuid UUID NOT NULL,
 code_hash TEXT NOT NULL CHECK (code_hash ~ '^[a-f0-9]{64}$'),
 proof_hash TEXT NOT NULL CHECK (proof_hash ~ '^[a-f0-9]{64}$'),
 discovered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 redeemed_at TIMESTAMPTZ
);
ALTER TABLE dune_quest_private.first_finder OWNER TO dune_quest_owner;
REVOKE ALL ON TABLE dune_quest_private.first_finder FROM PUBLIC,dune_cp;
GRANT USAGE ON SCHEMA public TO dune_quest_owner;
GRANT SELECT(uuid,balance), INSERT(uuid,balance), UPDATE(balance) ON public.dune_economy_balances TO dune_quest_owner;

CREATE OR REPLACE FUNCTION public.dune_quest_register(p_account UUID,p_minecraft UUID,p_code_hash TEXT,p_proof_hash TEXT)
RETURNS TABLE(outcome TEXT) LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE winner dune_quest_private.first_finder%ROWTYPE;
BEGIN
 IF p_account IS NULL OR p_minecraft IS NULL OR p_code_hash IS NULL OR p_proof_hash IS NULL
 OR p_code_hash !~ '^[a-f0-9]{64}$' OR p_proof_hash !~ '^[a-f0-9]{64}$' THEN
  RAISE EXCEPTION 'Invalid discovery' USING ERRCODE='22023';
 END IF;
 INSERT INTO dune_quest_private.first_finder(quest_id,account_id,minecraft_uuid,code_hash,proof_hash)
 VALUES('imperial-portal-eye-ra-v1',p_account,p_minecraft,p_code_hash,p_proof_hash)
 ON CONFLICT(quest_id) DO NOTHING;
 -- The unique quest key arbitrates simultaneous first discoveries globally.
 SELECT * INTO STRICT winner FROM dune_quest_private.first_finder WHERE quest_id='imperial-portal-eye-ra-v1' FOR UPDATE;
 IF winner.account_id<>p_account OR winner.minecraft_uuid<>p_minecraft THEN RETURN QUERY SELECT 'taken'::TEXT;RETURN;END IF;
 IF winner.redeemed_at IS NOT NULL THEN RETURN QUERY SELECT 'redeemed'::TEXT;RETURN;END IF;
 IF winner.code_hash=p_code_hash AND winner.proof_hash=p_proof_hash THEN RETURN QUERY SELECT 'issued'::TEXT;RETURN;END IF;
 -- The same winner may rediscover after a lost response. Its old code is revoked.
 UPDATE dune_quest_private.first_finder SET code_hash=p_code_hash,proof_hash=p_proof_hash WHERE quest_id='imperial-portal-eye-ra-v1';
 RETURN QUERY SELECT 'reissued'::TEXT;
END $$;

CREATE OR REPLACE FUNCTION public.dune_quest_claim(p_account UUID,p_minecraft UUID,p_code_hash TEXT)
RETURNS TABLE(outcome TEXT) LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE winner dune_quest_private.first_finder%ROWTYPE;
BEGIN
 IF p_account IS NULL OR p_minecraft IS NULL OR p_code_hash IS NULL OR p_code_hash !~ '^[a-f0-9]{64}$' THEN
  RAISE EXCEPTION 'Invalid redemption' USING ERRCODE='22023';
 END IF;
 SELECT * INTO winner FROM dune_quest_private.first_finder WHERE quest_id='imperial-portal-eye-ra-v1' FOR UPDATE;
 IF NOT FOUND THEN RETURN QUERY SELECT 'invalid'::TEXT;RETURN;END IF;
 IF winner.account_id<>p_account OR winner.minecraft_uuid<>p_minecraft OR winner.code_hash<>p_code_hash THEN RETURN QUERY SELECT 'invalid'::TEXT;RETURN;END IF;
 IF winner.redeemed_at IS NOT NULL THEN RETURN QUERY SELECT 'already_redeemed'::TEXT;RETURN;END IF;
 -- Exactly one fixed 1,000-Dunemark credit and its receipt share this transaction.
 INSERT INTO public.dune_economy_balances AS wallet(uuid,balance) VALUES(p_minecraft,1000.00)
 ON CONFLICT(uuid) DO UPDATE SET balance=wallet.balance+1000.00;
 UPDATE dune_quest_private.first_finder SET redeemed_at=clock_timestamp() WHERE quest_id='imperial-portal-eye-ra-v1';
 RETURN QUERY SELECT 'credited'::TEXT;
END $$;

CREATE OR REPLACE FUNCTION public.dune_quest_status(p_account UUID,p_minecraft UUID)
RETURNS TABLE(available BOOLEAN,discovered BOOLEAN,redeemed BOOLEAN) LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT NOT EXISTS(SELECT 1 FROM dune_quest_private.first_finder),
 EXISTS(SELECT 1 FROM dune_quest_private.first_finder WHERE account_id=p_account AND minecraft_uuid=p_minecraft),
 EXISTS(SELECT 1 FROM dune_quest_private.first_finder WHERE account_id=p_account AND minecraft_uuid=p_minecraft AND redeemed_at IS NOT NULL)
$$;
ALTER FUNCTION public.dune_quest_register(UUID,UUID,TEXT,TEXT) OWNER TO dune_quest_owner;
ALTER FUNCTION public.dune_quest_claim(UUID,UUID,TEXT) OWNER TO dune_quest_owner;
ALTER FUNCTION public.dune_quest_status(UUID,UUID) OWNER TO dune_quest_owner;
REVOKE ALL ON FUNCTION public.dune_quest_register(UUID,UUID,TEXT,TEXT),public.dune_quest_claim(UUID,UUID,TEXT),public.dune_quest_status(UUID,UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.dune_quest_register(UUID,UUID,TEXT,TEXT),public.dune_quest_claim(UUID,UUID,TEXT),public.dune_quest_status(UUID,UUID) TO dune_cp;
COMMIT;
