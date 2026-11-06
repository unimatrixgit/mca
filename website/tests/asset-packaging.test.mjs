import test from 'node:test';import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';
test('built web assets retain the approved seal image without publishing its internal provenance note',async()=>{
 const bundle=await readFile(new URL('../dist/server/index.js',import.meta.url),'utf8');
 assert(bundle.includes('/assets/seals/network-staff-mr_blueray-v2-150.png'));
 assert(!bundle.includes('/assets/seals/mr-blueray-provenance.txt'));
});
