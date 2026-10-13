# AI_CALL — Phase 10: India Bill of Materials and Landed Cost Assessment

## 1. Executive Summary

This document presents the detailed, itemized Bill of Materials (BOM) for the Raspberry Pi Zero 2 W Bluetooth HFP audio bridge, tailored specifically for procurement in **India** using authorized distributors and major retailers.

> [!CAUTION]
> **NO PURCHASES HAVE BEEN MADE.** In accordance with strict project change control, this is a planning document only. No orders will be placed, no funds will be spent, and no software will be downloaded without explicit user authorization.

---

## 2. Itemized Bill of Materials (India Domestic Market)

| Item # | Component Description | Primary Indian Source / Distributor | Listed Base Price (INR) | GST (18%) | Total Estimated Price (INR) | Source URL & Notes |
| :---: | :--- | :--- | :---: | :---: | :---: | :--- |
| **1** | **Raspberry Pi Zero 2 W** (Single Board Computer, 512MB RAM, Quad-Core 64-bit, BT 4.2 Classic) | **Silverline Electronics** *(Approved Reseller)* | ₹1,500.00 | ₹270.00 | **₹1,770.00** | [silverlineelectronics.in](https://silverlineelectronics.in)<br/>*Alternative: Thingbits.in or Robu.in (~₹1,500 - ₹1,800 + GST)* |
| **2** | **SanDisk Ultra 32GB MicroSD Card** (Class 10, U1, A1, up to 120MB/s) | **Amazon.in / Robu.in** | ₹338.98 | ₹61.02 | **₹400.00** | [amazon.in](https://www.amazon.in)<br/>*Provides OS storage & high IOPS for Linux logs* |
| **3** | **Micro-USB to USB-A High-Speed Data Cable** (Supports 480Mbps data transfer for USB OTG Gadget mode) | **Amazon.in / Local Retail** | ₹127.12 | ₹22.88 | **₹150.00** | *Must be a full 4-wire data cable, not power-only* |
| **4** | **Power Supply (Optional Standalone)** | **N/A (Host PC USB)** | ₹0.00 | ₹0.00 | **₹0.00** | *Powered directly from Host PC USB 3.0 port (5V 900mA; Pi draws ~300mA)* |
| **5** | **Mini-HDMI to HDMI Adapter / OTG Cable** | **N/A (Headless OS)** | ₹0.00 | ₹0.00 | **₹0.00** | *Not required; configured 100% headless via SSH over USB Ethernet* |
| **6** | **Domestic Courier Shipping** (Surface/Air inside India) | **Standard Courier** | ₹84.75 | ₹15.25 | **₹100.00** | *Estimated standard domestic shipping charge* |

---

## 3. Total Cost Summary

| Cost Category | Total Amount (INR) | Equivalent (USD) |
| :--- | :---: | :---: |
| **Subtotal (Hardware Components)** | ₹2,050.00 | ~$24.60 |
| **Applicable Taxes (GST 18%)** | ₹370.00 | ~$4.45 |
| **Domestic Shipping & Handling** | ₹100.00 | ~$1.20 |
| **TOTAL ESTIMATED ONE-TIME COST** | **₹2,420.00** | **~$29.00** |
| **RECURRING MONTHLY COST** | **₹0.00 / month** | **$0.00 / month** |

*Note: USD conversion calculated at benchmark rate of 1 USD ≈ 83.5 INR.*

---

## 4. Availability and Stock Status in India

1. **Raspberry Pi Zero 2 W Availability:**
   - Authorized Indian resellers (**Silverline Electronics**, **Thingbits Electronics**, **Robu.in**) maintain periodic inventory. 
   - When bare boards are temporarily out of stock, boards with pre-soldered GPIO headers (**Raspberry Pi Zero 2 WH**) are frequently available for an additional ₹150–₹250 (approx. ₹1,950 total incl. GST). The presence of GPIO headers does not affect USB or Bluetooth functionality.
2. **Component Reuse Opportunities:**
   - If the user already possesses a spare 16GB or 32GB MicroSD card and a standard Micro-USB data cable at home, the required cash outlay drops to strictly the single-board computer: **~₹1,870 INR total** (including shipping).

---

## 5. Comparison With Commercial Telephony Alternatives

| Metric | Option 2: Raspberry Pi Zero 2 W Bridge | Option 4: Cloud Telephony DID (Equal AI Model) |
| :--- | :---: | :---: |
| **Initial Hardware Outlay** | **₹2,420 INR** (One-time) | ₹0.00 |
| **Monthly Subscription Cost** | **₹0.00 / month** | **₹1,500 – ₹2,800 INR / month** ($18 – $35/mo) |
| **Year 1 Total Cost of Ownership** | **₹2,420 INR** | **₹18,000 – ₹33,600 INR** |
| **Offline Privacy** | **100% Local / Zero Cloud Leakage** | 0% (Audio processed on cloud servers) |
| **SIM Dependency** | **Retains Existing Physical SIM on Phone** | Requires carrier conditional forwarding (`*67*`) |

---

## 6. Pre-Purchase Warning

This budget is presented for user evaluation. **No hardware should be purchased until the user reviews the physical test checkpoints and grants explicit authorization.**
