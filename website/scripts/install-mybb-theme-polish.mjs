import {readFileSync,writeFileSync,copyFileSync,existsSync} from 'node:fs';
const integrations=process.argv[2]||'/home/dunerealms/apps/integrations';
const source=process.argv[3]||new URL('../integrations/mybb/dune-forum-polish.css',import.meta.url);
const helper=integrations+'/shared-header.php';
let php=readFileSync(helper,'utf8');
const marker='// DuneRealms native MyBB surface polish';
if(!php.includes(marker)){
 const anchor=' return $html;';
 if(!php.includes(anchor))throw Error('Shared presentation return anchor missing');
 const addition=` ${marker}
 if(($_SERVER['HTTP_HOST']??'')==='forums.dunerealms.com'){
  $html=str_ireplace('</head>','<link rel="stylesheet" href="/dune-forum-polish.css?v=1"></head>',$html);
  $html=preg_replace_callback('/<body\\b([^>]*)>/i',function($m){$attrs=$m[1];if(preg_match('/\\bclass=["\\\']([^"\\\']*)["\\\']/i',$attrs))$attrs=preg_replace('/\\bclass=(["\\\'])([^"\\\']*)\\1/i','class="$2 dune-forum"',$attrs,1);else $attrs.=' class="dune-forum"';return '<body'.$attrs.'>';},$html,1);
 }
`;
 copyFileSync(helper,helper+'.before-mybb-polish');
 php=php.replace(anchor,addition+anchor);writeFileSync(helper,php);
}
if(!existsSync(integrations+'/mybb'))throw Error('MyBB directory missing');
copyFileSync(source,integrations+'/mybb/dune-forum-polish.css');
console.log('Native MyBB surface polish installed; authentication and signature handlers untouched.');
