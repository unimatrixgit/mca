import sys,json
sys.path.insert(0,'/opt/dunerealms/server-control')
import agent
with agent.network_database() as con:
 with con.cursor() as cur:
  cur.execute('SELECT CONSTRAINT_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME LIKE %s',('dune_kit_sales%',));print(json.dumps(cur.fetchall()))
  cur.execute('SELECT COUNT(*) AS n,COALESCE(SUM(gross_cents),0) AS gross,COALESCE(SUM(server_tax_cents),0) AS server,COALESCE(SUM(faction_tax_cents),0) AS faction,COALESCE(SUM(seller_net_cents),0) AS seller FROM dune_kit_sales');print(json.dumps(cur.fetchone(),default=str))
