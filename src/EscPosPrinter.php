<?php

namespace Blutrixx\EscPosPrinter;

class EscPosPrinter
{
    public function checkPermissions(): array
    {
        return $this->call('EscPosPrinter.CheckPermissions');
    }

    public function requestPermissions(): array
    {
        return $this->call('EscPosPrinter.RequestPermissions');
    }

    public function connect(): array
    {
        return $this->call('EscPosPrinter.Connect');
    }

    public function disconnect(): array
    {
        return $this->call('EscPosPrinter.Disconnect');
    }

    public function isConnected(): bool
    {
        $result = $this->call('EscPosPrinter.IsConnected');

        return $result['connected'] ?? false;
    }

    public function getPrinters(): array
    {
        $result = $this->call('EscPosPrinter.GetPrinters');

        return $result['printers'] ?? [];
    }

    /**
     * Ensure printer is connected before printing.
     */
    public function ensureConnected(): array
    {
        if ($this->isConnected()) {
            return ['connected' => true];
        }

        $connectResult = $this->connect();

        if (isset($connectResult['error'])) {
            return $connectResult;
        }

        return ['connected' => true, 'device' => $connectResult['device'] ?? 'unknown'];
    }

    public function print(string $commands): array
    {
        // 1. Connect first — printer instance needed for logo conversion
        $connectionResult = $this->ensureConnected();
        if (isset($connectionResult['error'])) {
            return $connectionResult;
        }

        // 2. Now convert logo (needs printer to be connected for bitmap→hex)
        $commands = $this->processLogoInCommands($commands);

        // 3. Strip only truly empty <img></img> tags (no content between tags)
        $commands = preg_replace('/\[C\]\s*<img>\s*<\/img>\s*\n?/', '', $commands);

        return $this->call('EscPosPrinter.Print', ['commands' => $commands]);
    }

    public function testPrint(): array
    {
        $connectionResult = $this->ensureConnected();
        if (isset($connectionResult['error'])) {
            return $connectionResult;
        }

        return $this->call('EscPosPrinter.TestPrint');
    }

    /**
     * Replace {{LOGO:filename.png}} or {{LOGO}} placeholder with actual logo hex data.
     * Must be called AFTER printer is connected (DantSu needs printer instance).
     *
     * Supported formats:
     *   {{LOGO}}              → loads assets/images/{config('escpos-printer.default_logo')}
     *   {{LOGO:custom.png}}   → loads assets/images/custom.png
     *
     * There is no package-shipped default logo — each consuming app bundles
     * its own asset and points `escpos-printer.default_logo` (or the
     * ESCPOS_PRINTER_DEFAULT_LOGO env var) at it. If neither an explicit
     * filename nor a configured default is available, the logo line is
     * silently dropped rather than falling back to any particular app's asset.
     */
    public function processLogoInCommands(string $commands): string
    {
        if (! preg_match('/\{\{LOGO(?::([^}]+))?\}\}/', $commands, $match)) {
            return $commands;
        }

        $placeholder = $match[0];
        $filename = $match[1] ?? config('escpos-printer.default_logo');

        if (empty($filename)) {
            return preg_replace('/.*\{\{LOGO(?::[^}]+)?\}\}.*\n?/', '', $commands);
        }

        $logoResult = $this->convertLogo($filename);
        $logoHex = $logoResult['hex'] ?? '';

        if (empty($logoHex)) {
            // Remove the entire logo line if conversion failed
            return preg_replace('/.*\{\{LOGO(?::[^}]+)?\}\}.*\n?/', '', $commands);
        }

        return str_replace($placeholder, $logoHex, $commands);
    }

    /**
     * Convert an image from assets/images/ to ESC/POS hex string.
     *
     * @param string $filename  Image filename inside the app's bundled assets/images/
     */
    public function convertLogo(string $filename): array
    {
        return $this->call('EscPosPrinter.ConvertLogo', ['filename' => $filename]);
    }

    private function call(string $function, array $params = []): array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call($function, json_encode($params));

            return json_decode($result, true) ?? [];
        }

        return ['status' => false, 'message' => 'NativePHP bridge not available'];
    }
}
