<?php
// Reuse osTicket's public topic catalogue; never publish private staff topics.
function dune_support_topic_menu($selected = 'appeal') {
    $topics = Topic::getPublicHelpTopics();
    if (!$topics) return;
    $escape = static fn($text) => htmlspecialchars((string)$text, ENT_QUOTES, 'UTF-8');
    echo '<section class="dune-topic-header dune-appeal-topics"><p class="dune-compose-eyebrow">DUNEREALMS / SUPPORT DESK</p><h2>Choose a support topic</h2><p>Wrong topic? Select the right one below before submitting your request.</p><nav class="dune-topic-menu" aria-label="Ticket topic">';
    foreach ($topics as $id => $name) {
        $appeal = (int)$id === 2;
        $url = $appeal ? '/appeal.php' : '/open.php?topicId='.(int)$id;
        echo '<a class="dune-topic-choice" href="'.$escape($url).'"'.($appeal && $selected === 'appeal' ? ' aria-current="page"' : '').'>'.$escape($name).'</a>';
    }
    echo '</nav></section>';
}
