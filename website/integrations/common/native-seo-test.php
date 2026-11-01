<?php
require $argv[1]??__DIR__.'/native-seo.php';
function check($yes,$message){if(!$yes)throw new Exception($message);}
$html='<html><head><title>Old</title><meta name="description" content="generic"><meta name="robots" content="index"><link rel="canonical" href="https://old.invalid/"></head><body>Original message</body></html>';
$_SERVER['HTTP_HOST']='support.dunerealms.com';$_SERVER['REQUEST_URI']='/index.php?tracking=ignored';http_response_code(200);
$public=dune_native_seo($html);check(str_contains($public,'href="https://support.dunerealms.com/"'),'Public canonical');check(substr_count($public,'name="robots"')===1,'Single robots');check(str_contains($public,'index, follow, max-image-preview:large'),'Public indexable');check(str_contains($public,'Original message'),'Message unchanged');check(!str_contains($public,'old.invalid'),'Stale canonical removed');
$_SERVER['REQUEST_URI']='/scp/tickets.php?id=8';$private=dune_native_seo($html);check(str_contains($private,'noindex, nofollow, noarchive'),'Private noindex');check(!str_contains($private,'rel="canonical"'),'No public canonical on ticket');
$_SERVER['REQUEST_URI']='/service-targets.php';http_response_code(404);check(str_contains(dune_native_seo($html),'noindex, nofollow, noarchive'),'Error noindex');
$_SERVER['HTTP_HOST']='forums.dunerealms.com';$_SERVER['REQUEST_URI']='/showthread.php?tid=1';http_response_code(200);check(str_contains(dune_native_seo($html),'noindex, nofollow, noarchive'),'Account-gated forums remain private');
$_SERVER['HTTP_HOST']='cp.dunerealms.com';check(dune_native_seo($html)===$html,'Unrelated hosts unchanged');
echo "Native SEO public/private/error/canonical/body checks passed.\n";
