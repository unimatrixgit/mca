<?php
// Delivery-only fallback. Never use this address to identify or merge users.
function dune_delivery_address($address) {
 $address=(string)$address;
 if(preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}(?:@accounts\.dunerealms\.invalid)?$/i',$address))return 'no_reply@dunerealms.com';
 return $address;
}
