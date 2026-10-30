<?php

namespace App\Services;

use Illuminate\Support\Facades\Hash;

class PasswordHashService
{
    /**
     * Hash a password using Argon2id
     */
    public function hash(string $password): string
    {
        return Hash::make($password, [
            'memory' => 65536,
            'time' => 4,
            'threads' => 3,
        ]);
    }

    /**
     * Verify a password against a hash
     */
    public function verify(string $password, string $hash): bool
    {
        return Hash::check($password, $hash);
    }

    /**
     * Check if a password needs rehashing
     */
    public function needsRehash(string $hash): bool
    {
        return Hash::needsRehash($hash);
    }
}