<?php

namespace Blutrixx\EscPosPrinter\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static array checkPermissions()
 * @method static array requestPermissions()
 * @method static array connect()
 * @method static array disconnect()
 * @method static bool isConnected()
 * @method static array getPrinters()
 * @method static array print(string $commands)
 * @method static array testPrint()
 * @method static array convertLogo()
 * @method static string processLogoInCommands(string $commands)
 *
 * @see \Blutrixx\EscPosPrinter\EscPosPrinter
 */
class EscPosPrinter extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Blutrixx\EscPosPrinter\EscPosPrinter::class;
    }
}
