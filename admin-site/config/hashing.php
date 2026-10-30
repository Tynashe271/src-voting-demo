<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Default Hash Driver
    |--------------------------------------------------------------------------
    |
    | Explicitly argon2id per the specification's password security control.
    | Without this file Laravel silently falls back to bcrypt, which made
    | PasswordHashService's memory/time/threads options inert.
    |
    */

    'driver' => env('HASH_DRIVER', 'argon2id'),

    'bcrypt' => [
        'rounds' => env('BCRYPT_ROUNDS', 12),
        'verify' => true,
    ],

    'argon' => [
        'memory' => 65536,
        'threads' => 3,
        'time' => 4,
        'verify' => true,
    ],

];
