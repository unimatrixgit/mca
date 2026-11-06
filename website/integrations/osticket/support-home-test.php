<?php
class Topic {
    static $topics=[1=>'General support',2=>'Ban appeal',10=>'Report a Problem',11=>'Report a Problem / Access Issue',12=>'Forum support',14=>'Parental consent request [Preview]',15=>'Parental data removal [Preview]',18=>'Alpha Tester Signup',19=>'GDPR data removal request',21=>'Customer sales enquiries'];
    static function getPublicHelpTopics(){return self::$topics;}
}
function render(){ob_start();include __DIR__.'/dune-support-home.php';return ob_get_clean();}
function check($v){if(!$v)throw new RuntimeException('Support home regression');}
$html=render();check(substr_count($html,'class="dune-support-card"')===9);
$previous=-1;foreach(['Parental requests','GDPR data removal','General support','Appeal a ban','Become an Alpha tester'] as $name){$at=strpos($html,'<h2>'.$name.'</h2>');check($at!==false&&$at>$previous);$previous=$at;}
foreach(['/open.php?topicId=14','/open.php?topicId=15','/open.php?topicId=19','/open.php?topicId=1','/open.php?topicId=10','/open.php?topicId=11','/open.php?topicId=12','/open.php?topicId=21','/appeal.php','/alpha-signup.php','/view.php'] as $url)check(str_contains($html,'href="'.$url.'"'));
check(!str_contains($html,'Welcome to the Support Center'));check(str_contains($html,'does not automatically'));
Topic::$topics[22]='Another public branch / Access Issue';$ambiguous=render();check(!str_contains($ambiguous,'Get access help'));unset(Topic::$topics[22]);
unset(Topic::$topics[19]);$without=render();check(!str_contains($without,'Request data removal'));check(substr_count($without,'class="dune-support-card"')===8);
unset(Topic::$topics[21]);Topic::$topics[17]='Sales enquiries';Topic::$topics[20]='Development task';Topic::$topics[16]='Legal enquiries';$without=render();check(!str_contains($without,'Contact Sales'));foreach([16,17,20]as$id)check(!str_contains($without,'href="/open.php?topicId='.$id.'"'));
Topic::$topics=[];check(!str_contains(render(),'class="dune-support-card"'));
echo "Support home public-topic routing, priority, missing-topic and content checks passed\n";
