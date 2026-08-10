<?php

namespace Blutrixx\EscPosPrinter\Events;

class PrintFailed
{
    public function __construct(
        public readonly string $error,
        public readonly string $jobType = 'receipt',
    ) {}
}
