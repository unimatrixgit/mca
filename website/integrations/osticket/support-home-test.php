<?php
class Topic {
    static $topics=[1=>'General support',2=>'Ban appeal',14=>'Parental consent request [Preview]',15=>'Parental data removal [Preview]',18=>'Alpha Tester Signup',19=>'GDPR data removal request'];
    static function getPublicHelpTopics(){return self::$topics;}
}
function render(){ob_start();include __DIR__.'/dune-support-home.php';return ob_get_clean();}
function check($v){if(!$v)throw new RuntimeException('Support home regression');}
$html=render();check(substr_count($html,'class="dune-support-card"')===5);
$previous=-1;foreach(['Parental requests','GDPR data removal','General support','Appeal a ban','Become an Alpha tester'] as $name){$at=strpos($html,'<h2>'.$name.'</h2>');check($at!==false&&$at>$previous);$previous=$at;}
foreach(['/open.php?topicId=14','/open.php?topicId=15','/open.php?topicId=19','/open.php?topicId=1','/appeal.php','/alpha-signup.php','/view.php'] as $url)check(str_contains($html,'href="'.$url.'"'));
check(!str_contains($html,'Welcome to the Support Center'));check(str_contains($html,'does not automatically'));
unset(Topic::$topics[19]);$without=render();check(!str_contains($without,'Request data removal'));check(substr_count($without,'class="dune-support-card"')===4);
echo "Support home public-topic routing, priority, missing-topic and content checks passed\n";
