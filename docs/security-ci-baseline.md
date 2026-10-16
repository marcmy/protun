# CI and dependency-security baseline (2026-10-14)

The first run of the new workflow exposed **pre-existing** upstream problems; see
[the initial CI run](https://github.com/marcmy/protun/actions/runs/38618138520).

- **Rust build:** `cargo check --all-targets` fails in `pvpnclient 1.6.3`,
  with incompatible `proton-os-interface` versions 0.2.7 and 0.3.5
  in the graph and `muon 2.6.3`. Exact-pinning `pvpnclient 1.5.1` and
  testing `muon 2.4.1` and `2.0.3` did **not** resolve the mismatch.
  These initial experimental pins were reverted; the dependency conflict
  is retained here as a historical baseline (see resolution below).
- **Formatting:** Existing source files are not rustfmt-clean. Normalize in
  a separate, reviewed formatting-only change before making rustfmt mandatory.
- **RustSec:** The generated advisory report flagged
  [RUSTSEC-2023-0071](https://rustsec.org/advisories/RUSTSEC-2023-0071)
  in `rsa 0.9.10`, which the report says has no fixed upgrade.
  The report also noted unmaintained `bincode`, `paste`, and
  `rustls-pemfile`. Review dependency reachability and upstream fixes.
- **Gradle wrapper:** The integrity validation passed.

The library intentionally ignores `Cargo.lock`; the audit resolves a
temporary lockfile on CI. Proton's Cargo registry is configured at
`sparse+https://rust-registry.proton.me/index/`. Do not expose registry
credentials to untrusted pull requests.

## Rust compilation issue resolved (2026-10-15)

[Draft PR #23](https://github.com/marcmy/protun/pull/23) uses Proton's
published compatible pair `pvpnclient =4.0.0` and `muon =3.0.0`, with
`rand 0.10` and the corresponding local-agent API migration. Authentication
fork selector cookies are preserved across the Rust/UniFFI/Android boundary.

- [Strict host and Android-feature Rust checks and 40 library tests passed](https://github.com/marcmy/protun/actions/runs/38837960251).
- [Android ARM64 native library compilation, UniFFI Kotlin bindings, and Android Kotlin compilation passed](https://github.com/marcmy/protun/actions/runs/38838147565).
- The original expected-warning Rust compilation exemption is removed from
  `ci.yml` in PR #23; regressions now fail the regular CI workflow.
- The Android artifact job tests cross-compilation and source compilation,
  **not** production VPN connection behavior, signed AAR publication, or
  device-side testing.

Rust formatting and security audit findings remain separately recorded
and should be handled as independent work; this fix does not waive them.

