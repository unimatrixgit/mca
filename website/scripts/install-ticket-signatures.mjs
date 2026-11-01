import{readFile,writeFile,copyFile}from 'node:fs/promises';
const root='/home/dunerealms/apps/integrations/osticket';
for(const area of ['staff','client']){
 const file=`${root}/include/${area}/templates/thread-entry.tmpl.php`,source=await readFile(file,'utf8');
 const marker="<?php require_once ROOT_DIR.'ticket-signatures.php'; echo dune_ticket_entry_signature($entry); ?>";
 if(source.includes(marker))continue;
 const needle='<div><?php echo $entry->getBody()->toHtml(); ?></div>';
 if(source.split(needle).length!==2)throw Error('Unexpected native thread template; left untouched');
 await copyFile(file,file+'.before-dune-signatures');await writeFile(file,source.replace(needle,needle+'\n        '+marker));
}
console.log('Current staff signatures displayed on existing and future thread entries; message bodies unchanged.');
