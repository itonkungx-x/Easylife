# Easylife in Siamcraft (Minecraft 26.3 / Fabric)

**ผู้พัฒนา / Authors:** น้องต้นสุดหล่อ & Chatgpt

มอดช่วยใช้ชีวิตใน Siamcraft: ลงประมูลแบบอัตโนมัติทีละช่อง ปรับค่าผ่าน Mod Menu และอัปเดตได้เอง
Quality-of-life client mod for Siamcraft: sequential auction listings, a bilingual config screen, and automatic updates.

## วิธีติดตั้งครั้งแรก / First installation

1. ติดตั้ง Fabric Loader สำหรับ Minecraft 26.3 และ Fabric API.
2. **ลบ SC-AutoAuction เวอร์ชันเก่าออกจาก mods ก่อน** เพื่อป้องกัน mod ID ซ้ำ.
3. ใส่ `Easylife-in-Siamcraft-26.3-0.2.6.jar` ในโฟลเดอร์ `.minecraft/mods`.
4. ติดตั้ง [Mod Menu 21.0.0](https://modrinth.com/mod/modmenu) หากต้องการหน้า Settings.
5. เข้าเกม → Mod Menu → Easylife in Siamcraft → Settings / ตั้งค่า.

## ตั้งค่าการประมูล / Auction configuration

| Setting | Default |
| --- | --- |
| Starting price | `100` |
| Minimum increase | `10%` |
| Buyout price | `0` |
| Auction duration | `60` seconds |
| Overtime | `11` seconds |
| Start | `after1m` |
| Currency | Vault |
| Override | Off |
| Delay between items | 20 ticks |

- กด **F8** เพื่อเริ่ม/หยุด, **F9** เพื่อหยุดฉุกเฉิน.
- ช่อง Inventory/Hotbar ที่มีไอเท็ม 1 ช่อง = คำขอลงประมูล 1 รายการ (หนึ่ง stack นับเป็นหนึ่งรายการ).
- เซิร์ฟเวอร์ไม่ดึงของออกจากมือเมื่อส่งเข้าคิว มอดจึงรอหน้า Dialog ปิดแล้วดำเนินการช่องต่อไป.
- ไอเท็มในช่อง Inventory จะถูกสลับมาถือชั่วคราวและคืนช่องเดิมหลังส่ง.
- ไฟล์ตั้งค่า: `config/easylife-in-siamcraft.json`.

## อัปเดตอัตโนมัติ / Automatic updates

1. มอดตรวจเวอร์ชันใหม่จาก **[GitHub Releases](https://github.com/itonkungx-x/Easylife/releases)** ของโครงการเท่านั้น.
2. ดาวน์โหลดไฟล์ `.jar` ของ Minecraft 26.3 จาก GitHub เมื่อมีเวอร์ชันที่ใหม่กว่า และเปิด Automatic updates.
3. ตรวจ SHA-256 digest, ขนาดไฟล์, Minecraft version, Fabric mod ID, และเวอร์ชันใน JAR ก่อนติดตั้ง.
4. เก็บ JAR ใหม่แยกจาก `mods` แล้วรอเกมปิด เพื่อให้ตัวช่วย Java ภายนอกติดตั้งแทนที่ไฟล์เดิมหลัง Minecraft ปิดสมบูรณ์.
5. เปิด Minecraft ใหม่เพื่อใช้เวอร์ชันล่าสุด; ดูสถานะใน Mod Menu Settings และบันทึกที่ `config/easylife-updater/install.log`.

**หมายเหตุ:** ต้องติดตั้ง 0.2.5 ด้วยตนเอง *ครั้งแรก* เนื่องจาก 0.1.1 ไม่มีระบบอัปเดตตัวเอง. การอัปเดตครั้งถัดไปไม่ต้องดาวน์โหลด JAR ด้วยตนเอง (ถ้าเกม/เครื่องอนุญาตให้เขียนไฟล์). ระบบไม่สามารถอัปเดต Minecraft, Fabric หรือ Mod Menu แทนได้.

**Notes:** Install v0.2.6 manually once; older v0.1.1 builds have no self-updater. Future versioned GitHub Releases auto-install on game shutdown. The mod only manages its own JAR.

## Release workflow

Update `mod_version` in `gradle.properties` and push to `main`. GitHub Actions builds and publishes a versioned release once for every new version. Published release assets are immutable; later commits with the same mod version do not overwrite a release.

## Safety

การปิดหน้า Dialog ไม่ได้ยืนยันว่าเซิร์ฟเวอร์รับคิวสำเร็จจริง ควรทดสอบทีละ 1–2 ชิ้นราคาถูก แล้วตรวจสอบรายการด้วยตัวเองก่อนใช้งานจำนวนมาก.
Closing the dialog only indicates the submit action happened; verify server-side queue acceptance separately. Test a couple of inexpensive items first.

Source / Issues: https://github.com/itonkungx-x/Easylife

## Modular architecture / โครงสร้างสำหรับระบบอื่น

`SCAutoAuctionClient` is now the lifecycle bootstrap rather than the auction algorithm.

- `AutomationModule`: common contract (`id`, `initialize`, `tick`, `stop`).
- `ModuleRegistry`: keeps registered modules independent and runs each tick.
- `AuctionModule`: auction-specific state machine; future workflow automation can be a new module without editing auction code.
- `EasyLifeConfig`: persistent shared configuration; new modules can introduce their own keys or dedicated config classes.
- `EasyLifeSettingsScreen`: staff-first compact Thai settings; new modules should have their own settings section.
- `EasyLifeUpdater`: common GitHub Releases updater and post-exit installer.

### Auction reliability change / แก้ปัญหาไอเท็มในมือเปลี่ยน
After server-side queue submission, Minecraft may update item components or stack count.
The auction module no longer requires byte-for-byte equivalence after submission.
Inventory swaps are still restored only when the current source and staging slot
have compatible item identities (or an empty slot after server consumption).
The mod stops if an unexpected different item occupies the staging slot, to avoid moving it into the wrong slot.

**ข้อจำกัด:** หน้าประมูลปิดไม่ได้ยืนยันว่าเซิร์ฟเวอร์รับรายการ จึงควรตรวจคิวจริง และทดสอบกับของราคาถูกก่อนลงทั้งหมด

## v0.2.5 — Crash fix & Price Extractor
- แก้ `IncompatibleClassChangeError: FabricLoader.getInstance() must be InterfaceMethodref` จากไฟล์ 0.2.4 ที่เคยประกอบ JAR ด้วย Stub API ผิดประเภท
- Rebuild with the **real** Fabric Loader API using Fabric Loom and Java 25. GitHub Actions verifies the `InterfaceMethodref` bytecode and refuses JARs that bundle FabricLoader.
- Preserves Auction / Price Extractor selector in Mod Menu. Selecting **Price Extractor** disables F8 auction submissions.
- Hover items on the **/worth** inventory. Open `http://127.0.0.1:8787` for live item and price details; `/data` for the latest item; `/items` for collected entries. These endpoints are local only.
- ⚠️ **Must manually install this fixed JAR once if you are on broken 0.2.4**, because that version crashes before its built-in updater can run. Remove old `0.2.4.jar` to prevent duplicate mod IDs.
- Build/test limitation: Java compilation, Fabric API linkage, and shutdown updater smoke tests pass in CI; live-game hover scanning is not fully validated on a Siamcraft server yet.

## v0.2.6 — โหมดแยก + โลโก้ Siam Craft

- ส่วนหัว Mod Menu มีเฉพาะ **โลโก้ Siam Craft** ตามภาพที่แนบ ไม่มีข้อความอื่น
- หน้าแรกแยก Auction และ Price Extractor พร้อมเปิด/ปิดอิสระ ใช้พร้อมกันก็ได้
- ปุ่มตั้งค่าแต่ละโมดูลเปิดหน้าแยก; แก้ปุ่มลัดด้วยหน้าควบคุมของ Minecraft (native Key Binds)
- F8 เริ่ม/หยุด Auction, F9 หยุดฉุกเฉิน, F7 เปิด/ปิด Price Extractor (ทั้งหมดเปลี่ยนปุ่มได้)
- Price Extractor สแกนไอเท็มที่เห็นในหน้า /worth โดยไม่ต้อง Hover โดยอ่าน component minecraft:lore จากข้อมูลที่เซิร์ฟเวอร์ส่ง
- แจ้งเตือนสรุปของใหม่ ราคาที่เปลี่ยน และยอดสะสมในแชต; ปิดแจ้งเตือนได้
- เว็บภายในเครื่อง: http://127.0.0.1:8787 มีเพียงรูปไอเท็มและราคา เรียงชื่อ A–Z, ราคาน้อยไปมาก หรือมากไปน้อย
- JSON: /items รายการสะสม และ /data รายการล่าสุด
- รูปบนเว็บใช้ asset ของ Minecraft 26.3 จาก mcasset.cloud (อาจไม่มีรูปของ Custom Item)
- **การเปลี่ยนหน้า /worth ยังต้องทำเอง**; หลังเปลี่ยนหน้า มอดสแกนช่องที่เห็นให้อัตโนมัติ
- ข้อมูลสแกนเก็บในหน่วยความจำขณะเกมเปิด ไม่อัปโหลดออกไปเซิร์ฟเวอร์ภายนอก
- หากเซิร์ฟเวอร์ไม่ส่งราคาผ่าน item lore จะยังไม่แสดงราคา ไม่สร้างตัวเลขขึ้นเอง
- Java 25 / Fabric Loader / updater ผ่าน CI แล้ว; ต้องทดสอบความถูกต้องกับปลั๊กอิน /worth ในเกมจริง
