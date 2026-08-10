<?php

return [
    /*
     * Filename (inside the consuming app's own bundled assets/images/) used
     * to resolve a bare {{LOGO}} placeholder. No default ships with this
     * package — each app supplies and bundles its own branded logo asset,
     * then points this at it (or passes a filename explicitly per call via
     * {{LOGO:filename.png}} / EscPosPrinter::convertLogo('filename.png')).
     * If left null and no explicit filename is given, the {{LOGO}} line is
     * silently stripped from the print job.
     */
    'default_logo' => env('ESCPOS_PRINTER_DEFAULT_LOGO'),
];
