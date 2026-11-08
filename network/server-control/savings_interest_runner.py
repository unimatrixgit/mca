#!/usr/bin/env python3
"""Hourly idempotent monthly savings settlement; game currency only."""
import json
import agent
from savings_interest import initialize,settle
from bank_transfers import EXCLUDED

def run():
 initialize(agent.network_database)
 with agent.network_database() as con:
  with con.cursor() as c:
   c.execute('SELECT b.player_uuid FROM dune_player_banks b JOIN dune_players p ON p.uuid=b.player_uuid ORDER BY b.player_uuid')
   players=[r['player_uuid'] for r in c.fetchall() if r['player_uuid'] not in EXCLUDED]
 counts={}
 for player in players:
  result=settle(agent.network_database,player,EXCLUDED);key=result['status'];counts[key]=counts.get(key,0)+1
 print(json.dumps({'monthlySavingsInterest':counts,'rate':'1.05% per calendar month','currency':'DM'}))
if __name__=='__main__':run()
