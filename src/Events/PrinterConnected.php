<?php

namespace Blutrixx\EscPosPrinter\Events;

class PrinterConnected
{
    public function __construct(
        public readonly string $deviceName,
    ) {}
}
