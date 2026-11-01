import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('MySQL reward procedures keep a fixed global winner and atomic fixed-size credit behind a definer boundary',async()=>{
 const sql=await readFile(new URL('../control-panel/quest-rewards-mysql.sql',import.meta.url),'utf8');
 assert.match(sql,/quest_id VARCHAR\(64\)[^\n]+PRIMARY KEY/);assert.match(sql,/CHECK \(quest_id='imperial-portal-eye-ra-v1'\)/);
 assert.equal((sql.match(/SQL SECURITY DEFINER/g)||[]).length,3);assert.equal((sql.match(/START TRANSACTION;/g)||[]).length,2);assert.equal((sql.match(/COMMIT;/g)||[]).length,2);
 assert.equal((sql.match(/EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END/g)||[]).length,2);assert.match(sql,/FOR UPDATE/g);assert.match(sql,/VALUES\(p_minecraft,1000\.00\)/);assert.match(sql,/balance=balance\+1000\.00/);
 assert.match(sql,/v_outcome='already_redeemed'/);assert.match(sql,/v_outcome='taken'/);assert.match(sql,/v_outcome='invalid'/);
 assert(!/p_amount|PREPARE |EXECUTE |GRANT (?:ALL|SELECT|INSERT|UPDATE|DELETE)/.test(sql.replace(/^--.*$/gm,'')));
});
test('MySQL integration verifier uses a newly generated isolated QA schema and always drops it',async()=>{
 const source=await readFile(new URL('../control-panel/quest-rewards-mysql-verify.py',import.meta.url),'utf8');
 assert.match(source,/schema = 'dune_quest_qa_' \+ secrets\.token_hex\(8\)/);assert.match(source,/finally:[\s\S]+DROP DATABASE/);assert.match(source,/ThreadPoolExecutor\(max_workers=2\)/g);
 assert.match(source,/qa_block_credit CHECK \(balance < 1000\)/);assert.match(source,/A failed credit consumed the code/);assert.match(source,/Another account could redeem/);assert.match(source,/Revoked code was accepted/);assert.match(source,/SELECT \* FROM dune_quest_first_finder/);
 assert(!/DROP DATABASE [`'"]?dunegameplay|TRUNCATE.*dune_quest_first_finder/.test(source));
});
