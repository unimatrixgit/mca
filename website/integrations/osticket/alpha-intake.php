<?php
// Alpha intake uses its structured answers, not a second issue form.
function dune_alpha_intake($topicId) {
    $topic = Topic::lookup((int)$topicId);
    if (!$topic || $topic->getName() !== 'Alpha Tester Signup') return;
    // Preserve server-issued field names across native session rotation.
    $applicationFields=[];
    foreach ($topic->getForms() as $definition) {
        foreach ($definition->getForm()->getFields() as $field) {
            if (str_starts_with((string)$field->get('name'),'alpha_')) $applicationFields[]=$field;
        }
    }
    if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        foreach ($applicationFields as $field) {
            $logical=$field->get('name');
            foreach ($_SESSION['dune_alpha_field_maps'] ?? [] as $mapping) {
                $old=$mapping[$logical] ?? '';
                if ($old && array_key_exists($old,$_POST)) {
                    $_POST[$field->getFormName()]=$_POST[$old];
                    $_POST[$logical]=$_POST[$old];
                    break;
                }
            }
        }
    }
    $mapping=[];
    foreach ($applicationFields as $field) $mapping[$field->get('name')]=$field->getFormName();
    $_SESSION['dune_alpha_field_maps'][]=$mapping;
    $_SESSION['dune_alpha_field_maps']=array_slice($_SESSION['dune_alpha_field_maps'],-5);
    $form = TicketForm::objects()->one()->getForm();
    $values = ['subject'=>'Alpha tester application', 'message'=>'Alpha tester application submitted using the structured application form. Please review the application answers attached to this ticket.'];
    if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        foreach ($values as $name=>$value) {
            foreach ($form->getField($name)->getFormNames() as $key) $_POST[$key]=$value;
        }
    }
    if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        foreach ($topic->getForms() as $definition) $definition->getForm($_POST);
        TicketForm::objects()->one()->getForm($_POST);
    }
    $names=[];
    foreach ($values as $name=>$value) $names[]=$form->getField($name)->getFormName();
    $config=json_encode(['topic'=>(int)$topicId,'fields'=>$names], JSON_HEX_TAG|JSON_HEX_AMP|JSON_HEX_APOS|JSON_HEX_QUOT);
    ob_start(function($html)use($config){
        if (!str_contains($html,'</body>')) return $html;
        $script='<script>(()=>{const c='.$config.';const sync=()=>{const alpha=Number(document.getElementById("topicId")?.value)===c.topic;for(const name of c.fields){const field=document.getElementsByName(name)[0];if(!field)continue;const row=field.closest("tr");if(row){row.hidden=alpha;const header=row.previousElementSibling;if(header?.querySelector(".form-header"))header.hidden=alpha;}field.disabled=alpha;}};document.addEventListener("DOMContentLoaded",()=>{sync();document.getElementById("topicId")?.addEventListener("change",sync);const root=document.getElementById("dynamic-form");if(root)new MutationObserver(sync).observe(root,{childList:true,subtree:true});});})();</script>';
        return str_replace('</body>',$script.'</body>',$html);
    });
}



