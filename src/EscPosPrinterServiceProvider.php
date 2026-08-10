<?php

namespace Blutrixx\EscPosPrinter;

use Illuminate\Support\ServiceProvider;

class EscPosPrinterServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->mergeConfigFrom(__DIR__.'/../config/escpos-printer.php', 'escpos-printer');

        $this->app->singleton(EscPosPrinter::class, function () {
            return new EscPosPrinter;
        });

        $this->app->alias(EscPosPrinter::class, 'escpos-printer');
    }

    public function boot(): void
    {
        $this->publishes([
            __DIR__.'/../config/escpos-printer.php' => config_path('escpos-printer.php'),
        ], 'escpos-printer-config');
    }
}
