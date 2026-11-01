<?php
if(!defined('IN_MYBB'))die('Direct access denied');
$plugins->add_hook('index_end','dune_sections_index');
$plugins->add_hook('pre_output_page','dune_sections_style');
function dune_sections_style($html){return str_replace('</head>','<link rel="stylesheet" href="/dune-sections.css"></head>',$html);}
function dune_sections_info(){return ['name'=>'DuneRealms forum sections','description'=>'Category panels with native permission-aware forum rows and child links.','website'=>'https://www.dunerealms.com','author'=>'DuneRealms','version'=>'1.0.0','compatibility'=>'18*'];}
function dune_sections_escape($text){return htmlspecialchars((string)$text,ENT_QUOTES,'UTF-8');}
function dune_sections_visible($forum,$all){
 foreach(explode(',',$forum['parentlist']??(string)$forum['fid'])as$id){
  if(!isset($all[(int)$id])||empty($all[(int)$id]['active']))return false;
  $permissions=forum_permissions((int)$id);
  if(empty($permissions['canview'])||!forum_password_validated($all[(int)$id],true))return false;
 }
 return true;
}
function dune_sections_children($id,$all){
 $children=array_filter($all,fn($f)=>(int)$f['pid']===$id&&dune_sections_visible($f,$all));
 usort($children,fn($a,$b)=>((int)$a['disporder']<=>(int)$b['disporder'])?:((int)$a['fid']<=>(int)$b['fid']));
 return $children;
}
function dune_sections_links($children){
 return implode('',array_map(fn($f)=>'<a href="'.dune_sections_escape(get_forum_link($f['fid'])).'">'.dune_sections_escape($f['name']).'</a>',$children));
}
function dune_sections_index(){
 global $cache,$mybb,$forums,$fcache;
 if(empty($mybb->settings['dune_sections_enabled']))return;
 $all=$cache->read('forums');$roots=dune_sections_children(0,$all);$html='';
 foreach($roots as$root){
  $panels=dune_sections_children((int)$root['fid'],$all);if(!$panels)$panels=[$root];
  $html.='<section class="dune-board-category"><div class="dune-board-heading"><p>COMMUNITY FORUMS</p><h2>'.dune_sections_escape($root['name']).'</h2></div><div class="dune-board-children"><span>Subcategories</span>'.dune_sections_links($panels).'</div><div class="dune-board-panels">';
  foreach($panels as$panel){
   $children=dune_sections_children((int)$panel['fid'],$all);
   $html.='<section class="dune-board-panel"><div class="dune-board-panel-title"><img src="https://www.dunerealms.com/assets/nav-boards-v2.webp" alt="" width="44" height="44"><div><h3><a href="'.dune_sections_escape(get_forum_link($panel['fid'])).'">'.dune_sections_escape($panel['name']).'</a></h3><p>'.dune_sections_escape(strip_tags($panel['description'])).'</p></div><span>SUBCATEGORY</span></div>';
   $previous=$mybb->settings['hideprivateforums'];$mybb->settings['hideprivateforums']=1;
   try{
    if($children){$html.='<div class="dune-board-children"><span>Forums</span>'.dune_sections_links($children).'</div>';$rows=build_forumbits((int)$panel['fid'],2);}
    elseif($panel['type']==='f'){
     $copy=$panel;$copy['name']='Discussions';$copy['description']='';$virtual=-991;
     $before=$fcache[$virtual]??null;$fcache[$virtual]=[0=>[(int)$panel['fid']=>$copy]];
     try{$rows=build_forumbits($virtual,2);}finally{if($before===null)unset($fcache[$virtual]);else $fcache[$virtual]=$before;}
    }else $rows=null;
   }finally{$mybb->settings['hideprivateforums']=$previous;}
   if($rows&&!empty($rows['forum_list']))$html.='<table class="tborder dune-board-forums"><thead><tr><th colspan="2">Forum</th><th>Threads</th><th>Posts</th><th>Last post</th></tr></thead><tbody>'.$rows['forum_list'].'</tbody></table>';
   else $html.='<p class="dune-board-empty">No visible forums in this section.</p>';
   $html.='</section>';
  }
  $html.='</div></section>';
 }
 if($html)$forums=$html;
}