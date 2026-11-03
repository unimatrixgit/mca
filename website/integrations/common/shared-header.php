<?php
// Private shared presentation helper; contains no authentication or permission logic.
function dune_shared_header($html) {
 if(stripos($html,'<html')===false)return $html;
 if(!str_contains($html,'/dune-actions.css'))$html=str_ireplace('</head>','<link rel="stylesheet" href="/dune-actions.css?v=1"><script src="/dune-actions.js?v=1" defer></script></head>',$html);
 $base='/home/dunerealms/apps/integrations/';
 if(strpos($html,'class="dune-site-header"')===false){
  $header=file_get_contents($base.'shared-header.html');
  $html=preg_replace_callback('/<body\b[^>]*>/i',fn($m)=>$m[0].$header,$html,1);
 }
 if(strpos($html,'https://www.dunerealms.com/portal-navigation.js')===false){
  $head=file_get_contents($base.'shared-header-head.html');
  $html=str_ireplace('</head>',$head.'</head>',$html);
 }
 if(!str_contains($html,'class="dune-memorial"')&&is_file($base.'shared-memorial.html'))$html=preg_replace_callback('/<header class="dune-site-header"[\s\S]*?<\/header>/',fn($m)=>str_replace('</header>',file_get_contents($base.'shared-memorial.html').'</header>',$m[0]),$html,1);
 $html=preg_replace('~https://www\.dunerealms\.com/portal-navigation\.(js|css)(?:\?[^\"\s>]*)?~','/dune-navigation.$1?v=shared-20261102',$html);
 if(($_SERVER['HTTP_HOST']??'')==='support.dunerealms.com'&&!str_contains($_SERVER['SCRIPT_NAME']??'','/scp/'))$html=str_ireplace('</head>','<script src="/dune-appeal-routing.js" defer></script></head>',$html);
 if(($_SERVER['HTTP_HOST']??'')==='support.dunerealms.com'){
  $html=str_ireplace('</head>','<link rel="stylesheet" href="/dune-osticket-theme.css?v=20261103-alpha"></head>',$html);
  $html=str_ireplace('</head>','<link rel="stylesheet" href="/dune-ticket-compose.css?v=4"><script src="/dune-ticket-compose.js?v=4" defer></script></head>',$html);
  $html=str_ireplace('</head>','<link rel="stylesheet" href="/dune-support.css?v=signature-4"></head>',$html);
  $html=preg_replace('/<body\b(?![^>]*\bclass=)/i','<body class="dune-support"',$html,1);
 }
 if(($_SERVER['HTTP_HOST']??'')==='support.dunerealms.com')$html=preg_replace('~(?<=>)\s*[a-f0-9-]{36}@accounts\.dunerealms\.invalid\s*(?=<)~i','Website account',$html);
 // Alpha recruitment is a native private help-topic intake, not an automatic rank grant.
 if(($_SERVER['HTTP_HOST']??'')==='support.dunerealms.com' && str_contains($html,'id="landing_page"') && !str_contains($html,'dune-alpha-invitation')){
  $invite='<section class="dune-alpha-invitation"><img src="https://www.dunerealms.com/assets/news-community.webp" alt="" width="180" height="120"><div><p class="eyebrow">HELP SHAPE THE REALMS</p><h2>Become an Alpha tester</h2><p>Java or Bedrock, new or experienced: tell us what you would like to test. Applications are reviewed privately by Support.</p><a class="button" href="/alpha-signup.php">Sign up for Alpha testing</a></div></section>';
  $html=preg_replace_callback('/<div\b[^>]*id=["\']landing_page["\'][^>]*>/i',fn($m)=>$m[0].$invite,$html,1);
 }
 // DuneRealms native MyBB surface polish
 if(($_SERVER['HTTP_HOST']??'')==='forums.dunerealms.com'){
  $html=str_replace('/dune-navigation.js?v=shared-20261102','/dune-navigation.js?v=shared-20261102',$html);
  $html=str_ireplace('</head>','<link rel="stylesheet" href="/dune-forum-polish.css?v=1"></head>',$html);
  $html=preg_replace_callback('/<body\b([^>]*)>/i',function($m){$attrs=$m[1];if(preg_match('/\bclass=["\']([^"\']*)["\']/i',$attrs))$attrs=preg_replace('/\bclass=(["\'])([^"\']*)\1/i','class="$2 dune-forum"',$attrs,1);else $attrs.=' class="dune-forum"';return '<body'.$attrs.'>';},$html,1);
 }
 if(!str_contains($html,'class="dune-shared-footer"'))$html=str_ireplace('</body>',file_get_contents($base.'shared-footer.html').'</body>',$html);
 require_once __DIR__.'/native-seo.php';
 return dune_native_seo($html);
}

