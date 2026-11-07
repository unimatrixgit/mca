<?php
define('DUNE_RECOGNITION_CATALOG',dirname(__DIR__,3).'/node/recognition-catalog.json');
require dirname(__DIR__,2).'/recognition.php';
require dirname(__DIR__).'/forum-awards.php';
foreach([0=>0,1=>1,9=>1,10=>2,49=>2,50=>3,99=>3,100=>4,999=>4,1000=>5,1001=>6]as$count=>$expected)if(count(dune_forum_awards_for($count))!==$expected)throw new Exception('Forum award boundary '.$count);
if(count(dune_forum_awards_for(0,true))!==6)throw new Exception('Showcase incomplete');
if(!in_array('builder-award',dune_recognition_keys(),true))throw new Exception('Manual award absent');
foreach([['owners'],['game_builder'],['game_mod'],['web_dev'],['staff'],['official_account']]as$ranks)if(!dune_signature_staff_ranks($ranks))throw new Exception('Staff signature classification');
if(dune_signature_staff_ranks(['user','vip','patron_guild']))throw new Exception('Ordinary member classified staff');
$keys=['alpha-tester','builder-award','forum-posts-1','forum-legend'];
if(dune_signature_filter_awards($keys,true)!==['alpha-tester']||dune_signature_filter_awards($keys,false)!==$keys)throw new Exception('Signature awards policy failed');
if(dune_forum_awards_html(['username'=>'NoPosts','postnum'=>0])!=='')throw new Exception('Zero-post award');
$html=dune_forum_awards_html(['username'=>'Fixture','postnum'=>1001],true);
if(substr_count($html,'<img ')!==6||!str_contains($html,'Forum Legend')||str_contains($html,'<details>')||!str_contains($html,'flex-wrap:nowrap')||!str_contains($html,'min-width:0'))throw new Exception('Compact single-row awards incomplete');
$memprofile=['uid'=>9,'username'=>'DuneRealms','postnum'=>0];$profilefields='<div>Existing profile field</div>';dune_forum_profile_awards();
if(substr_count($profilefields,'<img ')!==6||!str_contains($profilefields,'Existing profile field'))throw new Exception('Profile showcase changed native fields');
class AwardDb {public $reads=0;function simple_select($table,$columns,$where){$this->reads++;if($table!=='users'||$where!=='uid=7')throw new Exception('Unsafe user query');return true;}function fetch_array($query){return['username'=>'Fixture','postnum'=>50];}}
$db=new AwardDb;$post=['uid'=>7,'groupimage'=>'Existing role'];$first=dune_forum_post_awards($post);$second=dune_forum_post_awards($post);
if($db->reads!==1||!str_starts_with($first['groupimage'],'Existing role')||substr_count($first['groupimage'],'<img ')!==3||$first!==$second)throw new Exception('Postbit award projection failed');
echo "Forum milestone and showcase checks passed\n";
