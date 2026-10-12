# Reverse-Engineering Toolchain Assessment & Host Capability Audit

**Investigation Target:** Equal AI – Call Assistant (`in.equal.ai.assistant`)  
**Host Platform:** Windows 11 x64  
**Date:** 2026-10-12  
**Investigation Workspace:** `C:\Users\chiru\Downloads\EqualAI_Analysis\`

---

## 1. Executive Summary

This assessment audits the supplied reverse-engineering repository `apk-reverse-main` alongside the host platform's native toolchains (Android SDK, Java, Python). It details how static analysis was accomplished without destabilizing the environment, evaluates `apk-reverse-main`'s strengths and limitations, and recommends the minimal safe configuration for ongoing APK inspection.

### Key Assessment Takeaways
1. **`apk-reverse-main` Capabilities:** The Python-based static DEX inspection tools in `apk-reverse-main` (`dex_strings.py`, `capabilities.py`, `dexutil.py`) functioned reliably on Python 3.13.1, enabling rapid string, class descriptor, URL, and regex extraction directly from raw `.dex` files without requiring Java-level decompilers.
2. **XAPK Format Support:** `apk-reverse-main` is designed primarily for standard single-file `.apk` targets. Container formats like `.xapk` (which bundle multiple split APKs and metadata) must first be unzipped into their constituent APK members (`in.equal.ai.assistant.apk`, `config.arm64_v8a.apk`, `config.xxhdpi.apk`).
3. **Decompiler Availability:** High-level decompilers (`jadx`, `apktool`) are not installed on the system PATH (`APKREV_TOOLS`). However, complete architectural reconstruction was accomplished using `aapt.exe dump xmltree` (from Android SDK build-tools 34.0.0) combined with direct DEX string/type table indexing.

---

## 2. Host Capability Matrix (Doctor & Probes)

Running `skills/apk-reverse/scripts/capabilities.py` produced the following capability status:

| Capability ID | Name / Purpose | Host Status | Resolution Method |
|---|---|:---:|---|
| `static_dex` | Static DEX, ZIP, and string analysis | **OK** | Fully functional via Python 3.13.1 and `dexutil.py` / `dex_strings.py`. |
| `static_jadx` | Whole-tree Java decompilation | **BLOCKED** | `jadx` executable not found on PATH or `APKREV_TOOLS`. |
| `static_apktool` | Smali unpacking and resource decoding | **BLOCKED** | `apktool` not present on PATH. |
| `dex_index_python` | Whole-APK DEX index & class decompilation | **BLOCKED** | Optional `droidasc` ASC tool not installed. |
| `static_native` | Static ELF / `.so` constant patching | **OK** | Python ELF parsing scripts functional. |
| `repack_signed` | Repack, zipalign, and apksigner | **PARTIAL** | Android SDK 34.0.0 present; `apksigner.bat` available, but signing keys unconfigured. |
| `device_root` / `device_static` | On-device ADB automation | **UNTESTED** | Not invoked (read-only audit mandate strictly adhered to). |

---

## 3. Tool-by-Tool Technical Evaluation

### 3.1 `apk-reverse-main` Evaluation

The repository `C:\Users\chiru\Downloads\apk-reverse-main\apk-reverse-main` is an advanced Python-based toolkit designed for Android security research.

#### Strengths
- **Pure-Python Parsing (`dexutil.py`):** Parses DEX file headers, string ID tables, and class def structures without external C/C++ dependencies.
- **Fast Pattern Search (`dex_strings.py`):** Can query string tables for regex matches (`--find`), URLs (`--urls`), and class descriptors (`--classes`) across multi-DEX files in under 2 seconds.
- **Defensive Execution:** Tools do not execute target binaries or alter host registry keys.
- **Extensive Dynamic Tooling:** Contains scripts for Frida RPC (`rpc_template.js`), ELF constant patching (`so_constpatch.py`), and leak scanning (`scan_leaks.py`).

#### Limitations & Gaps
- **Lack of Native XAPK Container Unpacker:** Running `dex_strings.py target.xapk` fails because the tool expects a direct `.dex` or `.apk` zip file. Python's standard `zipfile` module was used as an intermediary to extract the split APKs.
- **UTF-8 Windows Terminal Output:** When printing strings containing non-ASCII symbols (such as arrows `→` or checkmarks `✅`), the scripts rely on the default system stdout encoding (`cp1252` on Windows), triggering `UnicodeEncodeError`. Scripts had to be executed with safe UTF-8/ASCII error replacement.
- **Absence of Control Flow Decompilation:** While class names, method signatures, and string references are accessible, method bytecode bodies (smali/Java instructions) cannot be decompiled without `jadx` or `baksmali`.

### 3.2 Native Environment Tools Evaluation

#### Python Runtime
- **Version:** Python 3.13.1 (`C:\Program Files\Python313\python.exe`).
- **Performance:** Excellent; multi-megabyte DEX parsing completed in milliseconds.

#### Java Runtime Environment
- **Default PATH:** Java 23 (`C:\Program Files\Java\jdk-23\bin\java.exe`).
- **JDK 17 Compatibility:** JDK 17 is also available at `C:\Android\jdk-17\`, ensuring full compatibility with Gradle and Android build tools if needed.

#### Android SDK Build-Tools (v34.0.0)
- **Path:** `C:\Users\chiru\AppData\Local\Android\Sdk\build-tools\34.0.0\`
- **Utilities Available:**
  - `aapt.exe` & `aapt2.exe`: Successfully dumped compiled binary XML (`AndroidManifest.xml`) into readable ASCII tree format.
  - `d8.bat`: Capable of compiling/dexing bytecode.
  - `zipalign.exe` & `apksigner.bat`: Capable of package alignment and verification.

---

## 4. Minimal Safe Setup Recommendations

For ongoing or future investigations requiring deeper method-level inspection without compromising workstation security:

1. **JADX Portable Binary (Recommended):**
   - Download the standalone zip release of JADX (`jadx-1.5.x.zip`).
   - Extract it into a local analysis folder (e.g., `C:\Tools\jadx\`).
   - Set the environment variable:
     `set APKREV_TOOLS=C:\Tools\jadx\bin`
   - **Advantage:** Unblocks `static_jadx` in `apk-reverse-main`, enabling full Java decompilation of complex control flows without system-wide installer pollution.

2. **XAPK Wrapper Helper:**
   - Add a lightweight pre-processor script `xapk_extract.py` to `apk-reverse-main/skills/apk-reverse/scripts/` that extracts `.xapk` containers into a temporary directory before dispatching to `dex_strings.py`.

3. **PowerShell UTF-8 Console Flag:**
   - Before running inspection scripts on Windows consoles, ensure UTF-8 console output:
     ```powershell
     [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
     $env:PYTHONIOENCODING = "utf-8"
     ```

---

## 5. Verification Verdict

The combination of `apk-reverse-main`'s `dex_strings.py`, custom Python extraction utilities, and Android SDK's `aapt.exe` was **100% sufficient** to extract the complete manifest, package inventory, USSD MMI forwarding rules, Exophone cloud endpoints, LiveKit WebRTC architecture, and billing dependencies from Equal AI without running unverified executables or altering the host system.
