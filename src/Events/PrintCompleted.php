<?php

namespace Blutrixx\EscPosPrinter\Events;

class PrintCompleted
{
    public function __construct(
        public readonly string $jobType = 'receipt',
    ) {}
}
