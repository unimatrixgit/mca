<?php
// Public catalogue only: no private ticket information is rendered here.
$publicTopics = Topic::getPublicHelpTopics();
$topicUrl = static function ($name) use ($publicTopics) {
    foreach ($publicTopics as $id => $label) if ($label === $name) return '/open.php?topicId='.(int)$id;
    return null;
};
$cards = [
 ['Parental requests','FOR PARENTS & GUARDIANS','Request parental consent or ask about a child’s account. The Legal team reviews requests manually; this form does not automatically grant consent or change an account.','Parental consent request [Preview]','Open parental request','seals/department-legal-v2-150.png'],
 ['GDPR data removal','PRIVACY & ACCOUNT DATA','Ask the Legal team to review deletion of your account data. Identify the relevant account and explain your request. Ownership and scope are verified before any action. Do not upload passwords or identity documents.','GDPR data removal request','Request data removal','seals/department-legal-150.png'],
 ['General support','ACCOUNT, WEBSITE & GAME HELP','Tell Support what went wrong, where it happened, when it happened and what you tried. Include the error message or a relevant screenshot without passwords or unrelated personal information.','General support','Get support','nav-support-full-v2.webp'],
 ['Sales enquiries','ORDERS & SHOP QUESTIONS','Ask Sales about an order, a shop item or a purchase issue. Include the order reference if available. Never send passwords, payment card details or account credentials.','Customer sales enquiries','Contact Sales','seals/department-sales-150.png'],
 ['Forum support','HELP WITH THE BOARDS','Ask for help using the community boards or explain a forum account problem. Include the relevant page and error message without private messages or unrelated personal information.','Forum support','Get forum support','seals/department-forum-support-150.png'],
 ['Report a problem','TECHNICAL ISSUES','Describe the issue, the page or world affected, when it happened and the steps needed to reproduce it. Attach only relevant screenshots or logs without credentials.','Report a Problem','Report a problem','nav-support-full-v2.webp'],
 ['Access issues','GET HELP SIGNING IN','Explain which DuneRealms service you cannot access and the error you see. Staff will verify account ownership before changing access. Do not share passwords or verification codes.','Access Issue','Get access help','nav-account-full-v2.webp'],
 ['Appeal a ban','REQUEST A MODERATION REVIEW','Use the ban ID to open a private appeal linked to the original ban. Explain the situation and attach relevant evidence. A pending appeal does not itself remove a ban.','Ban appeal','Open ban appeal','seals/department-ban-appeals-150.png'],
 ['Become an Alpha tester','HELP SHAPE THE REALMS','Java or Bedrock, new or experienced: tell us what you would like to test. Applications are reviewed privately by Support.','Alpha Tester Signup','Sign up for Alpha testing','news-community.webp'],
];
$esc = static fn($v) => htmlspecialchars((string)$v, ENT_QUOTES, 'UTF-8');
?>
<link rel="stylesheet" href="/dune-support-home.css?v=328-1">
<div id="dune-support-home" class="dune-support-home">
<div class="dune-support-options" aria-label="Support request types">
<?php foreach ($cards as [$title,$label,$description,$topic,$button,$art]):
 $url = $topicUrl($topic); if (!$url) continue;
 if ($topic === 'Ban appeal') $url = '/appeal.php';
 if ($topic === 'Alpha Tester Signup') $url = '/alpha-signup.php'; ?>
<section class="dune-support-card">
 <img src="https://www.dunerealms.com/assets/<?=$esc($art)?>" alt="" width="180" height="120" loading="lazy">
 <div><p class="eyebrow"><?=$esc($label)?></p><h2><?=$esc($title)?></h2><p><?=$esc($description)?></p><a class="button" href="<?=$esc($url)?>"><?=$esc($button)?></a>
 <?php if ($topic === 'Parental consent request [Preview]' && ($removal=$topicUrl('Parental data removal [Preview]'))): ?><a class="dune-secondary-request" href="<?=$esc($removal)?>">Child’s account data removal →</a><?php endif; ?>
 </div>
</section>
<?php endforeach; ?>
</div>
<p class="dune-support-track"><a href="/view.php">Check an existing ticket →</a> · Requests are private to the requester and authorised staff. Submitting a request does not automatically change an account.</p>
</div>
