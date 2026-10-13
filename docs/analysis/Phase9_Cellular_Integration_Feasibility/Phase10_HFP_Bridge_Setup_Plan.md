# AI_CALL — Phase 10: Raspberry Pi HFP Bridge Technical Setup Plan

## 1. Executive Summary

This document specifies the exact, step-by-step configuration plan to provision the **Raspberry Pi Zero 2 W** as a dedicated Bluetooth Hands-Free Profile (HFP) audio bridge.

The design relies entirely on **headless USB OTG networking**: the Pi is powered and communicates with the host Windows PC over a single standard USB cable. No external monitor, keyboard, Wi-Fi router, or paid software is required.

> [!IMPORTANT]
> This is a planned deployment procedure for the future Proof-of-Concept phase. Do not execute these steps until hardware procurement has been explicitly authorized.

---

## 2. Step-by-Step Setup Procedure

### Step 1: MicroSD Card OS Provisioning (Headless)
1. Insert the 32GB MicroSD card into the host PC card reader.
2. Open the official **Raspberry Pi Imager** tool on the Windows PC.
3. Select OS: **Raspberry Pi OS Lite (64-bit)** (Debian 12 Bookworm, no desktop GUI).
4. Click **Edit Settings** (gear icon) to configure headless access:
   - Hostname: `aicall-bridge.local`
   - Username / Password: Set secure credentials (e.g., `aicall` / `aicall2026`).
   - Enable SSH: Check **Enable SSH** (Use password authentication or public key).
   - *Leave Wi-Fi disabled* (All communication will occur over USB Ethernet).
5. Click **Write** and verify the flashed image.

---

### Step 2: Configure USB OTG Gadget Mode (`g_ether`)
Before removing the MicroSD card from the PC, edit two configuration files on the boot partition (`bootfs` / `boot`):

1. **Enable the DWC2 USB Controller Driver:**
   Open `config.txt` (or `/boot/firmware/config.txt` on Bookworm) in a text editor.
   Add the following line at the very bottom under `[all]`:
   ```ini
   dtoverlay=dwc2
   ```

2. **Load the Ethernet Gadget Kernel Module:**
   Open `cmdline.txt` (a single-line file).
   Directly after `rootwait`, insert `modules-load=dwc2,g_ether`:
   ```text
   console=serial0,115200 console=tty1 root=PARTUUID=... rootfstype=ext4 fsck.repair=yes rootwait modules-load=dwc2,g_ether quiet init=/usr/lib/raspberrypi-sys-mods/firstboot
   ```
   *Caution: `cmdline.txt` must remain strictly a single line of text with no line breaks.*

3. Safely eject the MicroSD card from the PC and insert it into the Raspberry Pi Zero 2 W.

---

### Step 3: Physical Interconnect & SSH Verification
1. Connect the MicroSD card to the Pi Zero 2 W.
2. Connect the Micro-USB data cable from the **data port** (the inner Micro-USB port labeled "USB", **not** the outer port labeled "PWR IN") directly into a USB 3.0 port on the host Windows PC.
3. The host PC supplies 5V power over the data cable; the green LED on the Pi will begin blinking as it boots.
4. On Windows, the device will enumerate as an `RNDIS / USB Ethernet / CDC-NCM` network adapter.
5. In Windows PowerShell, verify the connection:
   ```powershell
   ping aicall-bridge.local
   ssh aicall@aicall-bridge.local
   ```
6. Verify round-trip latency: ping response must be **<1.0 ms**.

---

### Step 4: Provision BlueZ & Linux Bluetooth Stack
Once logged into the Pi via SSH:

1. **Update package lists:**
   ```bash
   sudo apt update && sudo apt install -y bluez bluez-tools pipewire wireplumber libspa-0.2-bluetooth python3-pip
   ```

2. **Configure BlueZ Daemon for Hands-Free Profile:**
   Edit `/etc/bluetooth/main.conf`:
   ```ini
   [General]
   Name = AI_CALL_BRIDGE
   Class = 0x200408      # Audio / Handsfree Audio Device class
   DiscoverableTimeout = 0
   AlwaysPairable = true
   Experimental = true   # Mandatory for BlueZ HFP wideband speech endpoints
   ```

3. **Restart the Bluetooth daemon:**
   ```bash
   sudo systemctl restart bluetooth
   ```

---

### Step 5: Configure WirePlumber for HFP Hands-Free Client Role
Create the drop-in configuration file to instruct PipeWire and WirePlumber to operate as a Hands-Free client (`hfp_hf`):

```bash
sudo mkdir -p /etc/wireplumber/wireplumber.conf.d/
sudo tee /etc/wireplumber/wireplumber.conf.d/11-bluetooth-policy.conf > /dev/null << 'EOF'
monitor.bluez.properties = {
  ["bluez5.roles"] = "[ hfp_hf hsp_hs ]",
  ["bluez5.codecs"] = "[ msbc sbc ]",
  ["bluez5.enable-msbc"] = true,
  ["bluez5.enable-hw-volume"] = true,
  ["bluez5.hfphsp-backend"] = "native"
}
EOF
```

Restart user-space audio services:
```bash
systemctl --user restart pipewire wireplumber
```

---

### Step 6: Bluetooth Pairing with the iQOO Z9 Handset
1. Open Bluetooth settings on the **iQOO Z9 5G**.
2. On the Pi Zero 2 W via SSH, run `bluetoothctl`:
   ```bash
   bluetoothctl
   [bluetooth]# default-agent
   [bluetooth]# power on
   [bluetooth]# discoverable on
   [bluetooth]# pairable on
   ```
3. On the iQOO Z9, scan for devices and select **`AI_CALL_BRIDGE`**.
4. Confirm the 6-digit numeric pairing code on both the phone screen and `bluetoothctl`.
5. In `bluetoothctl`, trust the phone:
   ```bash
   [bluetooth]# trust <PHONE_BLUETOOTH_MAC>
   [bluetooth]# connect <PHONE_BLUETOOTH_MAC>
   ```
6. On the phone, verify that the device is connected for **"Call audio"**.

---

### Step 7: Deploy Lightweight Audio Relay Daemon (`audio_relay.py`)
To transfer audio between the PipeWire ALSA nodes and the host PC over the USB Ethernet connection, deploy a lightweight Python socket relay on the Pi:

```python
# /home/aicall/audio_relay.py
import socket
import subprocess

HOST_PC_IP = "192.168.7.1"
DOWNLINK_PORT = 9001  # Streams caller audio to PC
UPLINK_PORT = 9002    # Receives AI audio from PC

# Stream Downlink: PipeWire Capture -> Host PC
# pw-record captures 16kHz 16-bit Mono PCM from BlueZ input node
record_proc = subprocess.Popen(
    ["pw-record", "--format", "s16", "--rate", "16000", "--channels", "1", "-"],
    stdout=subprocess.PIPE
)

# Stream Uplink: Host PC -> PipeWire Playback
# pw-play injects 16kHz 16-bit Mono PCM into BlueZ output node
play_proc = subprocess.Popen(
    ["pw-play", "--format", "s16", "--rate", "16000", "--channels", "1", "-"],
    stdin=subprocess.PIPE
)
```

---

## 3. Architecture Ready State

Once this 7-step sequence is completed, the Raspberry Pi Zero 2 W acts as an autonomous digital bridge:
- Caller speech arriving from the cellular network via Bluetooth eSCO is immediately piped over USB to the host PC ASR engine.
- Synthesized English speech generated on the host PC is immediately injected back into the phone's microphone channel over Bluetooth eSCO.
- The phone's internal speakers remain 100% silent, completely avoiding acoustic feedback and hardware echo cancellation.
