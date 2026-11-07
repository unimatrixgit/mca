<?php
// Shared, read-only recognition catalogue. No privilege or rank decisions occur here.
function dune_recognition_catalog(){
 static$catalog;if($catalog!==null)return$catalog;
 $path=defined('DUNE_RECOGNITION_CATALOG')?DUNE_RECOGNITION_CATALOG:__DIR__.'/recognition-catalog.json';
 if(!is_file($path))$path=dirname(__DIR__).'/node/recognition-catalog.json';
 $catalog=json_decode(file_get_contents($path),true,16,JSON_THROW_ON_ERROR);
 return$catalog;
}
function dune_recognition_labels(){
 $labels=[];$c=dune_recognition_catalog();foreach(array_merge($c['manual'],$c['forum'])as$a)$labels[$a['key']]=$a['label'];return$labels;
}
function dune_recognition_keys(){return array_keys(dune_recognition_labels());}
function dune_forum_awards_for($posts,$showcase=false){
 $count=is_numeric($posts)&&(float)$posts>=0&&floor((float)$posts)===(float)$posts?(int)$posts:0;
 return array_values(array_filter(dune_recognition_catalog()['forum'],fn($a)=>$showcase||$count>=$a['threshold']));
}
