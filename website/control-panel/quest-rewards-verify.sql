-- Administrator-only integration check. Requires quest-rewards.sql first.
-- Everything, including the synthetic wallet and discovery, is rolled back.
-- Refuses to run after a real discovery exists; never clears a real winner.
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM dune_quest_private.first_finder) THEN RAISE EXCEPTION 'A real discovery exists; use an isolated test database'; END IF;
 IF EXISTS(SELECT 1 FROM public.dune_economy_balances WHERE uuid IN ('e48caf1b-ef29-4b8a-8466-bfce4f000001','e48caf1b-ef29-4b8a-8466-bfce4f000002')) THEN RAISE EXCEPTION 'Synthetic identity already exists';END IF;
 IF has_column_privilege('dune_cp','public.dune_economy_balances','balance','UPDATE') OR has_table_privilege('dune_cp','dune_quest_private.first_finder','SELECT') THEN RAISE EXCEPTION 'CP has broader permissions than intended';END IF;
END $$;
SET LOCAL ROLE dune_cp;
DO $$ DECLARE result TEXT; BEGIN
 SELECT outcome INTO result FROM public.dune_quest_register('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('a',64),repeat('b',64));
 IF result<>'issued' THEN RAISE EXCEPTION 'First discovery failed';END IF;
 SELECT outcome INTO result FROM public.dune_quest_register('e48caf1b-ef29-4b8a-8466-bfce4f000022','e48caf1b-ef29-4b8a-8466-bfce4f000002',repeat('c',64),repeat('d',64));
 IF result<>'taken' THEN RAISE EXCEPTION 'Second finder was accepted';END IF;
 SELECT outcome INTO result FROM public.dune_quest_claim('e48caf1b-ef29-4b8a-8466-bfce4f000022','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('a',64));
 IF result<>'invalid' THEN RAISE EXCEPTION 'Wrong account was accepted';END IF;
 SELECT outcome INTO result FROM public.dune_quest_claim('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000002',repeat('a',64));
 IF result<>'invalid' THEN RAISE EXCEPTION 'Wrong UUID was accepted';END IF;
 SELECT outcome INTO result FROM public.dune_quest_register('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('e',64),repeat('f',64));
 IF result<>'reissued' THEN RAISE EXCEPTION 'Recovery reissue failed';END IF;
 SELECT outcome INTO result FROM public.dune_quest_claim('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('a',64));
 IF result<>'invalid' THEN RAISE EXCEPTION 'Revoked code was accepted';END IF;
 SELECT outcome INTO result FROM public.dune_quest_claim('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('e',64));
 IF result<>'credited' THEN RAISE EXCEPTION 'First credit failed';END IF;
 SELECT outcome INTO result FROM public.dune_quest_claim('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('e',64));
 IF result<>'already_redeemed' THEN RAISE EXCEPTION 'Duplicate claim was accepted';END IF;
 SELECT outcome INTO result FROM public.dune_quest_register('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001',repeat('c',64),repeat('d',64));
 IF result<>'redeemed' THEN RAISE EXCEPTION 'Redeemed discovery was reissued';END IF;
 IF NOT EXISTS(SELECT 1 FROM public.dune_quest_status('e48caf1b-ef29-4b8a-8466-bfce4f000011','e48caf1b-ef29-4b8a-8466-bfce4f000001') WHERE NOT available AND discovered AND redeemed) THEN RAISE EXCEPTION 'Status mismatch';END IF;
END $$;
RESET ROLE;
DO $$ BEGIN
 IF (SELECT balance FROM public.dune_economy_balances WHERE uuid='e48caf1b-ef29-4b8a-8466-bfce4f000001') IS DISTINCT FROM 1000::NUMERIC THEN RAISE EXCEPTION 'Wallet was not credited exactly once';END IF;
 IF EXISTS(SELECT 1 FROM public.dune_economy_balances WHERE uuid='e48caf1b-ef29-4b8a-8466-bfce4f000002') THEN RAISE EXCEPTION 'Other finder wallet was created';END IF;
END $$;
ROLLBACK;
SELECT 'Quest integration check passed; all synthetic data rolled back' AS verification;
