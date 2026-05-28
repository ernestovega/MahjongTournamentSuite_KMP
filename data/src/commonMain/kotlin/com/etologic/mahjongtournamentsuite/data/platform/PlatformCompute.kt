package com.etologic.mahjongtournamentsuite.data.platform

/**
 * Best-effort CPU core count hint for tuning compute-heavy work.
 *
 * On some targets (e.g. Wasm/JS) true parallelism might be unavailable, so the
 * returned value is a conservative hint rather than a guarantee.
 */
expect fun platformCpuCount(): Int

