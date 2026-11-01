<?php
// Presentation metadata only; this helper never changes access control or content.
function dune_native_seo($html) {
 if(stripos($html,'<html')===false)return $html;
 $host=strtolower(explode(':',$_SERVER['HTTP_HOST']??'')[0]);
 if(!in_array($host,['support.dunerealms.com','forums.dunerealms.com'],true))return $html;
 $path=parse_url($_SERVER['REQUEST_URI']??'/',PHP_URL_PATH)?:'/';
 $public=$host==='support.dunerealms.com'&&in_array($path,['/','/index.php','/service-targets.php'],true)&&http_response_code()<400;
 $robots=$public?'index, follow, max-image-preview:large':'noindex, nofollow, noarchive';
 if(!$public&&!headers_sent())header('X-Robots-Tag: noindex, nofollow, noarchive');
 $html=preg_replace('~<meta\b[^>]*name=["\'](?:robots|googlebot)["\'][^>]*>~i','',$html);
 $html=preg_replace('~<link\b[^>]*rel=["\']canonical["\'][^>]*>~i','',$html);
 $metadata='<meta name="robots" content="'.$robots.'">';
 if($public){
  $targets=$path==='/service-targets.php';
  $canonical='https://support.dunerealms.com/'.($targets?'service-targets.php':'');
  $title=$targets?'Support service targets | DuneRealms':'DuneRealms Support Center | Account and game help';
  $description=$targets?'See DuneRealms service targets for support, forum reports, ban appeals, sales and legal enquiries. Find the right department for your request.':'Get help with your DuneRealms account and Minecraft server experience. Open a support request, review service targets or find the ban appeal process.';
  $html=preg_replace('~<title>.*?</title>~si','<title>'.htmlspecialchars($title,ENT_QUOTES,'UTF-8').'</title>',$html,1);
  $html=preg_replace('~<meta\b[^>]*name=["\'](?:description|keywords)["\'][^>]*>~i','',$html);
  $metadata.='<link rel="canonical" href="'.$canonical.'"><meta name="description" content="'.htmlspecialchars($description,ENT_QUOTES,'UTF-8').'">';
  $metadata.='<meta property="og:type" content="website"><meta property="og:site_name" content="DuneRealms"><meta property="og:url" content="'.$canonical.'"><meta property="og:title" content="'.htmlspecialchars($title,ENT_QUOTES,'UTF-8').'"><meta property="og:description" content="'.htmlspecialchars($description,ENT_QUOTES,'UTF-8').'">';
  $metadata.='<meta property="og:image" content="https://www.dunerealms.com/assets/dunerealms-season-logo-v2.png">';
  $structured=['@context'=>'https://schema.org','@type'=>'WebPage','name'=>$title,'description'=>$description,'url'=>$canonical,'isPartOf'=>['@type'=>'WebSite','name'=>'DuneRealms','url'=>'https://www.dunerealms.com/']];
  $metadata.='<script type="application/ld+json">'.json_encode($structured,JSON_UNESCAPED_SLASHES|JSON_HEX_TAG|JSON_HEX_AMP|JSON_HEX_APOS|JSON_HEX_QUOT).'</script>';
 }
 return str_ireplace('</head>',$metadata.'</head>',$html);
}
