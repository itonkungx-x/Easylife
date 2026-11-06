# SC-AutoAuction — Fabric 26.3 (v0.1.1)

Client-side Minecraft mod for the auction queue dialog opened by `/auction queue add`.

**The auction queue does NOT consume the held item.** In v0.1.1 the mod waits for the server-side dialog to close after pressing the green submit button, then proceeds to the next slot. It does **not** require items to disappear from the hand.

## Default listing settings

| Setting | Value |
| --- | --- |
| Starting price | `100` |
| Minimum bid increase | `10%` |
| Buyout price | `0` |
| Auction duration | `60` seconds |
| Overtime | `11` seconds |
| Start | `after1m` |
| Currency | `Vault` |
| Override | `Off` |

**One occupied hotbar/inventory slot equals one auction queue submission**, whether it contains a single item or a full stack. Armor and offhand slots are not included. The mod snapshots occupied slots when F8 is pressed and processes each slot only once per run.

For slots outside the hotbar, the mod temporarily swaps the target slot with the current selected hotbar slot, submits the queue entry, and swaps it back. All original items remain in the same slots if successful.

## Install and use

1. Install Fabric Loader and compatible Fabric API for Minecraft 26.3, then place `SC-AutoAuction-26.3-0.1.1.jar` in `.minecraft/mods`.
2. Remove duplicate/older SC-AutoAuction jars.
3. Put the items you want queued in inventory/hotbar (1 occupied slot = 1 listing).
4. Close open screens, then press **F8** to start/stop, or **F9** to stop.
5. Review the auction queue on the server to ensure it accepted every request. **Dialog closure only indicates the request was submitted, not guaranteed auction acceptance**.

The mod stops if dialog controls, inventory states, or server responses differ from expected patterns, and never automatically retries an ambiguous submission. If interrupted during an inventory swap, inspect the inventory and put items back in their original slots before retrying.

## Build

Use Java 25 and Gradle 9.6.1: `gradle build`.
GitHub Actions uploads a compiled JAR artifact on commits to `main`.

**Status:** Compiles through GitHub Actions. Test on your server with 1–2 inexpensive items before running bulk auctions.
