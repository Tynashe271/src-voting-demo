<?php

namespace App\Exceptions;

use Exception;

/**
 * Raised when the Go voting backend's internal admin API rejects or fails a request. Carries the
 * upstream HTTP status and message so a controller can surface the same status/message to staff
 * without needing to know anything about the HTTP client underneath.
 */
class VotingApiException extends Exception
{
    public function __construct(
        public readonly int $status,
        string $message,
    ) {
        parent::__construct($message);
    }
}
