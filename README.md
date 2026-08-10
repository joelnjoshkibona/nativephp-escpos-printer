# Blutrixx ESC/POS Printer

A [NativePHP Mobile](https://nativephp.com) plugin for talking to Bluetooth ESC/POS thermal receipt printers — connect, print formatted text with an optional logo, and get notified of connection/print state via native events.

Composer package: `blutrixx/nativephp-escpos-printer`
Repo: `joelnjoshkibona/nativephp-escpos-printer`
Current release: `v1.0.0`

## Requirements

- PHP ^8.2
- A Laravel app running under `nativephp/mobile`
- Android: pulls in `com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0` via JitPack (repository auto-registered by this plugin's manifest) and requests Bluetooth + location permissions (see [Android manifest](#android-manifest) below).
- iOS: declares `NSBluetoothAlwaysUsageDescription` / `NSBluetoothPeripheralUsageDescription` and the `bluetooth-central` background mode.

## Installation

Not on Packagist yet.

**As a git submodule (how this repo itself consumes it):**

```bash
git submodule add https://github.com/joelnjoshkibona/nativephp-escpos-printer.git packages/nativephp-escpos-printer
```

```json
// composer.json
{
    "repositories": [
        {"type": "path", "url": "packages/nativephp-escpos-printer"}
    ],
    "require": {
        "blutrixx/nativephp-escpos-printer": "@dev"
    }
}
```

**Without a submodule:**

```json
{
    "repositories": [
        {"type": "vcs", "url": "https://github.com/joelnjoshkibona/nativephp-escpos-printer"}
    ],
    "require": {
        "blutrixx/nativephp-escpos-printer": "^1.0"
    }
}
```

Laravel auto-discovers `EscPosPrinterServiceProvider`.

## How the bridge works

Unlike `blutrixx/nativephp-device-utils`, every method on this package's facade is **synchronous** — connect, print, and status checks all block and return a real result, no `native-event` listening required. Internally each call goes through `nativephp_call()` with JSON-encoded params and a JSON-decoded response.

```php
use Blutrixx\EscPosPrinter\Facades\EscPosPrinter;

if (! EscPosPrinter::isConnected()) {
    EscPosPrinter::connect();
}

EscPosPrinter::print("Hello, receipt!\n");
```

Three native events still exist for cases where you want to react to connection state changing *outside* a direct method call (e.g. the printer disconnecting mid-session) — listen for these the same way described in `blutrixx/nativephp-device-utils`' README if you need them.

## Logo printing — you must configure your own logo

`print()` and `testPrint()` support a `{{LOGO}}` or `{{LOGO:filename.png}}` placeholder inside the ESC/POS command string, which gets swapped for the actual printed bitmap. **This package ships no default logo asset** — that's deliberate. It used to (an earlier, single-app version of this plugin hardcoded one brand's logo as the default), but a shared plugin can't sensibly default to any one app's branding, so:

1. Publish the config: `php artisan vendor:publish --tag=escpos-printer-config`
2. Bundle your own logo image into your app's own native build assets (this package doesn't manage that for you — it only resolves a *filename* at print time; getting that file onto the device is your app's asset pipeline).
3. Point `config/escpos-printer.php`'s `default_logo` (or the `ESCPOS_PRINTER_DEFAULT_LOGO` env var) at that filename, **or** always pass it explicitly:

```php
EscPosPrinter::processLogoInCommands("[C]<img>{{LOGO:my-brand-logo.png}}</img>\n");
// or, with ESCPOS_PRINTER_DEFAULT_LOGO=my-brand-logo.png set:
EscPosPrinter::processLogoInCommands("[C]<img>{{LOGO}}</img>\n");
```

If neither an explicit filename nor a configured default is available, the `{{LOGO}}` line is silently dropped from the print job — the receipt still prints, just without a logo, rather than falling back to any particular app's image.

## API reference

| Method | Returns | Notes |
|---|---|---|
| `checkPermissions()` | `array` | Bluetooth + location permission status |
| `requestPermissions()` | `array` | Prompts for Bluetooth + location permissions |
| `connect()` | `array` | Connects to the first bonded Bluetooth printer found |
| `disconnect()` | `array` | |
| `isConnected()` | `bool` | |
| `getPrinters()` | `array` | List of bonded Bluetooth devices |
| `ensureConnected()` | `array` | Connects only if not already connected — safe to call before every print |
| `print(string $commands)` | `array` | Connects first if needed, resolves any `{{LOGO}}` placeholder, strips empty `<img></img>` tags, then prints |
| `testPrint()` | `array` | Prints a test receipt |
| `processLogoInCommands(string $commands)` | `string` | Resolves a `{{LOGO}}`/`{{LOGO:file.png}}` placeholder to hex; called automatically by `print()` |
| `convertLogo(string $filename)` | `array` | Converts an image (from your app's bundled assets) to an ESC/POS hex string — `{hex: string}` on success |

```php
use Blutrixx\EscPosPrinter\Facades\EscPosPrinter;

EscPosPrinter::ensureConnected();
EscPosPrinter::print("[C]<b>RECEIPT</b>\n[L]Item 1          $5.00\n");
EscPosPrinter::testPrint();
EscPosPrinter::disconnect();
```

## Events

All under `Blutrixx\EscPosPrinter\Events\*`: `PrinterConnected`, `PrinterDisconnected`, `PrintCompleted`, `PrintFailed`.

## Android manifest

```
BLUETOOTH, BLUETOOTH_ADMIN          (maxSdkVersion 30 — legacy, superseded by the two below on API 31+)
BLUETOOTH_CONNECT, BLUETOOTH_SCAN
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION   (required by BLUETOOTH_SCAN pre-Android-12)
```
Bluetooth hardware feature is declared `required: true` — apps embedding this plugin are assumed to genuinely need Bluetooth printing, not degrade gracefully without it.

## Quick start

```php
// routes/api.php
Route::post('/printer/print-receipt', function () {
    return \Blutrixx\EscPosPrinter\Facades\EscPosPrinter::print(
        "[C]<img>{{LOGO}}</img>\n" .
        "[C]<b>Thank you for your purchase</b>\n" .
        "[L]Total: $42.00\n"
    );
});
```
