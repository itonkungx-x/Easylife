# Easylife in Siamcraft (Minecraft 26.3 / Fabric)

**ผู้พัฒนา / Authors:** น้องต้นสุดหล่อ & Chatgpt

มอดช่วยใช้ชีวิตใน Siamcraft: ลงประมูลแบบอัตโนมัติทีละช่อง ปรับค่าผ่าน Mod Menu และอัปเดตได้เอง
Quality-of-life client mod for Siamcraft: sequential auction listings, a bilingual config screen, and automatic updates.

## วิธีติดตั้งครั้งแรก / First installation

1. ติดตั้ง Fabric Loader สำหรับ Minecraft 26.3 และ Fabric API.
2. **ลบ SC-AutoAuction เวอร์ชันเก่าออกจาก mods ก่อน** เพื่อป้องกัน mod ID ซ้ำ.
3. ใส่ `Easylife-in-Siamcraft-26.3-0.2.0.jar` ในโฟลเดอร์ `.minecraft/mods`.
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

**หมายเหตุ:** ต้องติดตั้ง 0.2.0 ด้วยตนเอง *ครั้งแรก* เนื่องจาก 0.1.1 ไม่มีระบบอัปเดตตัวเอง. การอัปเดตครั้งถัดไปไม่ต้องดาวน์โหลด JAR ด้วยตนเอง (ถ้าเกม/เครื่องอนุญาตให้เขียนไฟล์). ระบบไม่สามารถอัปเดต Minecraft, Fabric หรือ Mod Menu แทนได้.

**Notes:** Install v0.2.0 manually once; older v0.1.1 builds have no self-updater. Future versioned GitHub Releases auto-install on game shutdown. The mod only manages its own JAR.

## Release workflow

Update `mod_version` in `gradle.properties` and push to `main`. GitHub Actions builds and publishes a versioned release once for every new version. Published release assets are immutable; later commits with the same mod version do not overwrite a release.

## Safety

การปิดหน้า Dialog ไม่ได้ยืนยันว่าเซิร์ฟเวอร์รับคิวสำเร็จจริง ควรทดสอบทีละ 1–2 ชิ้นราคาถูก แล้วตรวจสอบรายการด้วยตัวเองก่อนใช้งานจำนวนมาก.
Closing the dialog only indicates the submit action happened; verify server-side queue acceptance separately. Test a couple of inexpensive items first.

Source / Issues: https://github.com/itonkungx-x/Easylife
