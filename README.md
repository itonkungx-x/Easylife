# SC-AutoAuction — Fabric 26.3

Automates the Minecraft auction queue dialog opened by `/auction queue add`.

One occupied inventory/hotbar slot equals one listing. Full stacks count as one listing. Excludes armor/offhand. Server-authoritative inventory updates and fail-closed checks protect against accidental duplicate sends.

Preset: starting price `100`, minimum increment `10%`, buyout `0`, duration `60` seconds, overtime `11`, start `after1m`, currency `Vault`, override `Off`.

Press **F8** to start/stop, **F9** to stop. A safety timeout stops if a listing doesn't remove the held item. The bot must observe the correct six-field auction Dialog and valid submit button before acting. After use, check listings on the server.

Build with Java 25 and Gradle 9.6.1 using `gradle build`; GitHub Actions builds a .jar artifact automatically on push to main.

**Status:** Client-side source is provided for compilation. Must be tested against the live server dialog before relying on bulk listings.
