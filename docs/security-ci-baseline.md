# CI and dependency-security baseline (2026-10-14)

The first run of the new workflow exposed **pre-existing** upstream problems; see
[the initial CI run](https://github.com/marcmy/protun/actions/runs/38618138520).

- **Rust build:** `cargo check --all-targets` fails in `pvpnclient 1.6.3`,
  with incompatible `proton-os-interface` versions 0.2.7 and 0.3.5
  in the graph and `muon 2.6.3`. Resolve Proton registry dependency
  compatibility, then remove the `continue-on-error` from the `rust-check` job.
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

The initial workflow keeps the previously failing Rust compilation,
formatting and advisory reports informational while the new manifest and
Android wrapper validations remain gating checks. Re-enable strict checks
as soon as the baseline issues have been fixed.
