import test from 'node:test';
import assert from 'node:assert/strict';
import {submitApplication} from '../control-panel/election-applications.mjs';
test('deadline passing during lock wait prevents application and event insertion',async()=>{
 const player='12345678-1234-1234-1234-123456789abc',sql=[];
 const connection={async execute(query){sql.push(query);if(query.startsWith('SELECT value'))return[[{value:0}]];if(query.startsWith('SELECT minecraft_uuid'))return[[{minecraft_uuid:player}]];return[[]];}};
 const db={async batch(statements){return Promise.all(statements.map(statement=>statement.run(connection)));}};
 const start='2026-11-15T09:00:00Z';
 await assert.rejects(()=>submitApplication(db,{actor:{id:'owner'},context:{linked:true,faction:'atreides',playerUuid:player},input:{kind:'house',start,motivation:'I will serve the House and its members.',accepted:true},now:Date.parse(start)-1,clock:()=>Date.parse(start)}),/closed/);
 assert(sql.some(query=>query.includes('FOR UPDATE')));
 assert(!sql.some(query=>query.startsWith('INSERT INTO political_applications(')));
 assert(!sql.some(query=>query.startsWith('INSERT INTO political_application_events(')));
});
