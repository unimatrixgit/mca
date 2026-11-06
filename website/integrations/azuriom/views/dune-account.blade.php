@extends('layouts.app')

@section('title', 'DuneRealms account')

@section('content')
<div class="card mb-4">
    <div class="card-body p-4">
        <div class="d-flex flex-column flex-sm-row align-items-start align-items-sm-center gap-3 mb-3">
            <img src="https://www.dunerealms.com/assets/nav-account-v2.webp" width="88" height="88" alt="">
            <div>
                <p class="text-uppercase small mb-1">One identity throughout the realms</p>
                <h1 class="mb-0">Your DuneRealms account</h1>
            </div>
        </div>
        <p>Manage your profile, sign-in details and Minecraft account in the Account Center.</p>
        <div class="d-flex flex-wrap gap-2 mb-3">
            <a class="btn btn-primary" href="https://cp.dunerealms.com/#site-profile" target="_top">Profile &amp; biography</a>
            <a class="btn btn-primary" href="https://cp.dunerealms.com/#security" target="_top">Security, email &amp; password</a>
            <a class="btn btn-primary" href="https://cp.dunerealms.com/#game" target="_top">Minecraft identity</a>
        </div>
        <hr>
        <p class="mb-2">Looking for an order or subscription?</p>
        <a class="btn btn-primary" href="{{ route('shop.profile') }}">Purchase &amp; subscription history</a>
    </div>
</div>
@endsection
