# คู่มือสาธิต Centralized Banking Feature Control POC

คู่มือนี้เขียนสำหรับผู้พรีเซนต์ที่ต้องสาธิตระบบจริงจากเครื่อง Windows โดยใช้แอป Banking POC คู่กับ LaunchDarkly Dashboard ใช้เวลาประมาณ 20–25 นาที และมี Mock Mode เป็นแผนสำรองเมื่ออินเทอร์เน็ตหรือบัญชี LaunchDarkly ใช้งานไม่ได้

> ข้อมูลทั้งหมดในแอปเป็นข้อมูลสังเคราะห์ ไม่มีการโอนเงินจริง และห้ามใส่ข้อมูลลูกค้า เลขบัญชี เลขบัตร อีเมล เบอร์โทร หรือข้อมูลจริงใด ๆ ระหว่างเดโม

## สิ่งสำคัญที่ต้องจำก่อนเริ่ม

- Project ใน LaunchDarkly คือ `centrailized-banking` ชื่อนี้สะกดตาม Project ที่มีอยู่จริง
- Environment สำหรับเดโมคือ `devolopment` ชื่อนี้สะกดตาม Environment ที่มีอยู่จริง
- ห้ามแก้หรือเปิด Flag ใน Environment `Production`
- Live Mode ต้องเปลี่ยน Flag จาก LaunchDarkly Dashboard เท่านั้น
- ปุ่มในกล่อง `Presenter controls` ใช้ได้เฉพาะ Mock Mode และตั้งใจไม่ให้เขียนค่าไปยังบัญชี LaunchDarkly
- Browser รับเฉพาะ Client-side ID ส่วน SDK key อยู่ใน `dtm-service` เท่านั้น
- Flag ใช้ควบคุมการปล่อยฟีเจอร์ ไม่ใช่กลไกยืนยันตัวตนหรืออนุมัติธุรกรรม
- ถ้า Flag หรือ LaunchDarkly ใช้งานไม่ได้ ระบบต้องกลับสู่ค่าปลอดภัย: Legacy UI, Profile legacy, Fraud v1, Payment v1 และ Notification provider A/queued

## Flag ที่ใช้ในเดโม

| Flag | หน้าที่ | ค่าเป้าหมายของ Pilot | ค่าปลอดภัยเมื่อปิด |
|---|---|---|---|
| `client-new-payment-ui` | เปลี่ยนหน้าจอโอนเงินใน Browser | New UI | Legacy UI |
| `profile-response-v2` | เลือกรูปแบบ Profile response | v2 | legacy |
| `fraud-engine-version` | เลือก Fraud engine | v2 | v1 |
| `payment-api-migration` | กำหนดขั้นการย้าย Payment API | shadow/live/complete | off |
| `payment-v2-enabled` | Kill switch ของ Payment v2 | true | false หรือใช้ v1 |
| `notification-provider` | เลือก Notification provider | provider-b | provider-a |

กฎที่เตรียมไว้ใน Environment `Devolopment` คือ:

- `client-new-payment-ui`: Mali แบบ individual target, Segment `Bank Employees` และ Segment `Pilot Customers` ได้ New UI
- Flag ฝั่ง Server อีกห้าตัว: Segment `Pilot Customers` ได้ค่ารุ่นใหม่
- ทุก Flag ยังปิดอยู่ก่อนเริ่มเดโม เพื่อให้ระบบเริ่มจาก safe baseline

## Synthetic personas

| Persona ในแอป | Context key | ใช้สาธิต |
|---|---|---|
| Somchai · employee · preferred | `somchai-employee` | Segment พนักงานธนาคาร |
| Mali · pilot customer · iOS | `mali-pilot` | Individual target และ Pilot segment |
| Narin · general customer | `narin-general` | ผู้ใช้ทั่วไปที่ต้องอยู่ค่าเดิม |

## 1. เตรียมเครื่องก่อนวันเดโม

1. เปิด Docker Desktop และรอจนสถานะ Docker Engine พร้อมใช้งาน
2. เปิด PowerShell แล้วเข้าโฟลเดอร์โปรเจกต์:

   ```powershell
   cd D:\Work\LaunchDarkly\LAB\launchdarkly-banking-poc
   ```

3. ตรวจว่ามีไฟล์ `.env` และตั้ง `POC_MODE=launchdarkly` แล้ว ห้ามเปิดไฟล์นี้บนจอระหว่างนำเสนอ เพราะมี credential เฉพาะเครื่อง
4. สร้างหรืออัปเดต Docker image เมื่อโค้ดเปลี่ยน:

   ```powershell
   docker compose up -d --build
   ```

5. ตรวจ Container:

   ```powershell
   docker compose ps
   ```

   ต้องเห็น `dtm-service`, `payment-service`, `customer-profile-service`, `fraud-service`, `notification-service` และ `web-gateway` เป็น `Up`/`healthy`

6. เปิด `http://localhost:8080` และตรวจสิ่งต่อไปนี้:

   - Badge เป็น `LAUNCHDARKLY LIVE`
   - ข้อความเป็น `LaunchDarkly connected`
   - System status เป็น `5/5 healthy`
   - หน้าจอมือถือเป็น `LEGACY UI`

7. เปิด LaunchDarkly Dashboard อีกแท็บ แล้วเข้า:

   - Project: `Centrailized Banking`
   - เมนู `Features` → `Flags`
   - เลือก Environment `Devolopment`
   - ตรวจว่า Flag ทั้งหกตัวแสดง `Devolopment: Off`

8. จัดหน้าต่างสองหน้าต่างวางข้างกัน:

   - ซ้าย: LaunchDarkly Dashboard
   - ขวา: `http://localhost:8080`

9. ปิด Notification, โปรแกรมอีเมล และหน้าต่างที่อาจแสดง credential หรือข้อมูลส่วนตัว

### ถ้าปิดคอมแล้วเปิดใหม่

Docker image ยังอยู่ ไม่ต้อง Build ใหม่ทุกครั้ง ให้ทำดังนี้:

1. เปิด Docker Desktop
2. เปิด PowerShell และเข้าโฟลเดอร์โปรเจกต์
3. รัน:

   ```powershell
   docker compose up -d
   docker compose ps
   ```

4. เปิด `http://localhost:8080`

ถ้าแก้ source code, Dockerfile หรือ dependency หลังการ Build ครั้งล่าสุด ให้ใช้ `docker compose up -d --build` แทน บางเครื่องอาจเริ่ม Container ให้อัตโนมัติจาก `restart: unless-stopped`; ถึงอย่างนั้นควรรัน `docker compose ps` เพื่อตรวจสถานะก่อนเดโมทุกครั้ง

## 2. วิธีเปลี่ยน Flag ใน LaunchDarkly

ขั้นตอนพื้นฐานนี้ใช้ซ้ำตลอด Live Demo:

1. ใน LaunchDarkly ไปที่ `Features` → `Flags`
2. มองคอลัมน์ Environment และยืนยันว่าเลือก `Devolopment` ไม่ใช่ `Production`
3. คลิกชื่อ Flag ที่ต้องการ
4. อยู่ที่แท็บ `Targeting`
5. ถ้าต้องการดูกฎ ให้กด `View targeting rules`
6. เปิดหรือปิดสวิตช์ที่มีข้อความ `Flag is off`/`Flag is on`
7. กด `Review and save`
8. ตรวจ Environment และรายการเปลี่ยนแปลงอีกครั้ง แล้วกดปุ่มยืนยันการบันทึก เช่น `Save changes`
9. รอประมาณ 1–3 วินาที

ข้อสังเกต:

- `client-new-payment-ui` ส่งผลใน Browser ผ่าน streaming และมักเปลี่ยนโดยไม่ต้อง Refresh
- Flag ฝั่ง Server จะถูกประเมินใหม่เมื่อกด `Review & send` ในรายการถัดไป
- ตาราง `100-user rollout grid` โหลดจาก Gateway ตอนเปิดหน้า จึงต้อง Refresh หน้าเว็บหลังแก้ Percentage rollout
- ถ้าในหน้า LaunchDarkly แสดงทั้ง Production และ Devolopment ให้ยึดแถว/ตัวเลือก `Devolopment` เท่านั้น

## 3. สคริปต์ Live Demo แบบละเอียด

### ช่วงที่ 1 — เปิดเรื่องและชี้ Safe Baseline (นาที 0–2)

สิ่งที่กด:

1. ที่แอปกดแท็บ `Live demo`
2. เลือก `Narin · general customer`
3. ชี้ที่ Badge `LAUNCHDARKLY LIVE`, ข้อความ `LaunchDarkly connected` และ `5/5 healthy`
4. ชี้ที่ Chip `LEGACY UI` และหัวข้อ `Quick transfer`

สิ่งที่พูด:

> “นี่คือ Banking POC ที่ใช้ข้อมูลสังเคราะห์ทั้งหมด ตอนเริ่มต้น Flag ทุกตัวปิดอยู่ ระบบจึงใช้ค่าปลอดภัย ไม่ต้องพึ่งว่า Control Plane จะพร้อมเสมอ ตอนนี้ Browser เชื่อม LaunchDarkly อยู่ และบริการภายในทั้งห้าตัว Healthy”

> “เราจะแยกการตัดสินใจสองแบบ Flag ที่เปลี่ยนหน้าตา UI ประเมินใน Browser ด้วย Client-side ID ส่วนการตัดสินใจสำคัญของ Backend จะผ่าน Central DTM เท่านั้น SDK key ไม่ถูกส่งมาที่ Browser”

ผลที่ควรเห็น:

- Narin เห็น `LEGACY UI`
- Timeline ยังแสดง `Run a synthetic payment`
- ตาราง rollout เป็นค่า safe default

### ช่วงที่ 2 — อธิบาย Architecture (นาที 2–4)

สิ่งที่กด:

1. กดแท็บ `Architecture`
2. ชี้จาก `Browser banking POC` → `LaunchDarkly` → `Web Gateway` → `Central DTM`
3. ชี้บริการ `Profile`, `Fraud`, `Payment`, `Notification`

สิ่งที่พูด:

> “Browser มีเพียง Client-side ID และใช้ Flag ที่มีผลต่อการนำเสนอเท่านั้น ส่วนบริการ Java ไม่ผูกกับ LaunchDarkly SDK โดยตรง แต่เรียก DTM ผ่านสัญญาแบบ typed ทำให้รวม governance ไว้จุดเดียว และแต่ละบริการยังมี local fallback ของตัวเอง”

> “ถ้า DTM หรือ LaunchDarkly ช้า ระบบมี timeout, retry และ circuit breaker เพื่อไม่ให้ปัญหา Control Plane ลามเป็น Banking outage”

4. กดแท็บ `How it works`
5. ชี้หัวข้อ `Stable percentage rollout`, `Typed backend evaluation` และ `Failure without cascading`
6. กลับแท็บ `Live demo`

### ช่วงที่ 3 — Individual และ Segment Targeting ของ UI (นาที 4–7)

สิ่งที่กดใน LaunchDarkly:

1. ไปที่ `Features` → `Flags`
2. คลิก `Client New Payment UI`
3. ยืนยัน Environment `Devolopment`
4. กด `View targeting rules`
5. ชี้ให้ผู้ชมเห็น:

   - Individual target `mali-pilot` ได้ `New UI`
   - Rule `Bank Employees` ได้ `New UI`
   - Rule `Pilot Customers` ได้ `New UI`
   - ค่า default ยังเป็น `Legacy UI`

6. เปิดสวิตช์ Flag
7. กด `Review and save` แล้วกดยืนยันการบันทึก

สิ่งที่กดในแอป:

1. เลือก `Narin · general customer`
2. ชี้ว่า Narin ยังเป็น `LEGACY UI` และ `Quick transfer`
3. เลือก `Somchai · employee · preferred`
4. ชี้ว่าเปลี่ยนเป็น `NEW UI` และ `Smart transfer`
5. เลือก `Mali · pilot customer · iOS`
6. ชี้ว่า Mali ก็เป็น `NEW UI`

สิ่งที่พูด:

> “เราเปิด Flag เพียงครั้งเดียว แต่ผลไม่เหมือนกันทุกคน Narin ไม่เข้าเงื่อนไขจึงอยู่ Legacy ส่วน Somchai เข้า Segment พนักงาน และ Mali เข้าเงื่อนไข Pilot/individual target จึงได้ New UI ทันที โดยไม่ Deploy และไม่ Restart แอป”

> “บริบทเป็น multi-context มีทั้ง user และ device แต่ทั้งหมดเป็นข้อมูลสังเคราะห์ และ Feature Flag นี้ไม่ใช้ตัดสินสิทธิ์ทำธุรกรรม”

หมายเหตุ: Mali ตรงทั้ง individual target และ Pilot segment อยู่แล้ว หากต้องการพิสูจน์ individual target แบบแยกจาก Segment จริง ๆ ให้เตรียม Context เพิ่มก่อนวันเดโม อย่าแก้กฎสด ๆ ระหว่างนำเสนอโดยไม่ซ้อม

### ช่วงที่ 4 — เปิด Full Pilot Journey (นาที 7–10)

สิ่งที่กดใน LaunchDarkly:

เปิด Flag ต่อไปนี้ใน Environment `Devolopment` ทีละตัว โดยใช้ขั้นตอน `Targeting` → เปิดสวิตช์ → `Review and save` → ยืนยัน:

1. `Profile Response V2`
2. `Fraud Engine Version`
3. `Payment API Migration`
4. `Payment V2 Enabled`
5. `Notification Provider`

กฎ Pilot ที่เตรียมไว้จะให้ค่า:

- Profile = v2
- Fraud = v2
- Payment migration = shadow
- Payment v2 enabled = true
- Notification = provider-b

สิ่งที่กดในแอป:

1. เลือก `Mali · pilot customer · iOS`
2. ถ้ายังอยู่หน้าผลรายการเดิม ให้กด `New simulation`
3. คงผู้รับเป็น `Demo Merchant – Riverside`
4. คงจำนวนเงิน `1250.00`
5. กด `Review & send`
6. รอจนแสดงผลสำเร็จและดู `Decision timeline`

ผลที่ควรเห็นใน Timeline:

1. `Customer profile resolved` → v2
2. `Fraud engine selected` → v2
3. `Payment migration evaluated` → shadow
4. `Payment kill switch checked` → true
5. `Payment route completed` → `v1 authoritative; called v1 + v2; matched`
6. `Notification provider selected` → `provider-b · synthetic-notification-sent`

แต่ละแถวควรมี source เป็น `launchdarkly` และด้านบนมี `Trace xxxxxxxx`

สิ่งที่พูด:

> “หนึ่งรายการธุรกรรมสังเคราะห์ทำให้เราเห็นทุกการตัดสินใจตั้งแต่ Profile, Fraud, Payment จน Notification พร้อม source, reason และ correlation ID จุดนี้สำคัญ เพราะเราไม่ได้แค่เปิดฟีเจอร์ได้ แต่ตรวจย้อนหลังได้ว่าระบบตัดสินใจจากอะไร”

> “ขณะนี้ Payment อยู่ Shadow Mode ระบบเรียกทั้ง v1 และ v2 เพื่อเปรียบเทียบผล แต่ v1 ยังเป็นคำตอบ authoritative จึงเก็บข้อมูลความมั่นใจก่อนย้าย traffic จริง”

### ช่วงที่ 5 — Payment Migration: Shadow → Live → Complete (นาที 10–14)

ก่อนเริ่ม ให้ `Payment V2 Enabled` ยังเปิดอยู่

#### 5.1 Shadow

ค่าปัจจุบันของ Rule `Pilot Customers` ใน `Payment API Migration` คือ `shadow`

1. ในแอปกด `New simulation`
2. กด `Review & send`
3. ชี้แถว `Payment route completed`

ต้องเห็น:

- called = `v1 + v2`
- authoritative = `v1`
- comparison = `matched`

พูดว่า:

> “Shadow เรียก v2 แล้ว แต่ยังไม่ให้ v2 ตอบลูกค้า เราเปรียบเทียบผลกับ v1 ก่อน”

#### 5.2 Live

1. ใน LaunchDarkly เปิด Flag `Payment API Migration`
2. กด `View targeting rules`
3. ที่ Rule `Pilot Customers` กด `Edit`
4. เปลี่ยน variation จาก `shadow` เป็น `live`
5. กด `Review and save` แล้วกดยืนยัน
6. ในแอปกด `New simulation` → `Review & send`

ต้องเห็น:

- called = `v1 + v2`
- authoritative = `v2`
- comparison = `matched`

พูดว่า:

> “Live ยังเรียกทั้งสองรุ่นเพื่อเปรียบเทียบ แต่เปลี่ยน v2 ให้เป็น authoritative แล้ว การเปลี่ยนนี้เกิดจาก Control Plane โดยไม่ Deploy บริการ Payment ใหม่”

#### 5.3 Complete

1. กลับไป Rule `Pilot Customers`
2. กด `Edit`
3. เปลี่ยน variation เป็น `complete`
4. กด `Review and save` แล้วกดยืนยัน
5. ในแอปกด `New simulation` → `Review & send`

ต้องเห็น:

- called = `v2`
- authoritative = `v2`
- comparison = `not-compared`

พูดว่า:

> “เมื่อ Complete ระบบหยุดเรียก v1 ในเส้นทางปกติ ลดภาระการทำงานซ้ำ แต่เรายังเก็บ Kill Switch แยกไว้อีกชั้นสำหรับ rollback”

### ช่วงที่ 6 — Kill Switch และ Rollback (นาที 14–16)

สิ่งที่กด:

1. คง `Payment API Migration` เป็น `complete` หรือ `live`
2. ใน LaunchDarkly เปิด Flag `Payment V2 Enabled`
3. ปิดสวิตช์ Flag ใน Environment `Devolopment`
4. กด `Review and save` แล้วกดยืนยัน
5. ในแอปกด `New simulation` → `Review & send`

ผลที่ควรเห็น:

- `Payment kill switch checked` → false
- `Payment route completed` → v1 authoritative
- called = v1
- comparison มีข้อความ `v2-disabled-by-kill-switch`
- Journey อาจแสดงเป็น degraded เพื่อบอกอย่างตรงไปตรงมาว่าใช้ fallback

สิ่งที่พูด:

> “แม้ Migration จะไปถึง Live หรือ Complete แล้ว Kill Switch ยังมีอำนาจเหนือกว่า เมื่อปิด `payment-v2-enabled` ระบบถอยกลับ v1 ในรายการถัดไปทันที ไม่ต้องรอ Deploy rollback”

หลังจบส่วนนี้ ให้เปิด `Payment V2 Enabled` กลับ หากยังต้องสาธิต v2 ต่อ

### ช่วงที่ 7 — Percentage Rollout (ตัวเลือกเสริม นาที 16–18)

ส่วนนี้ใช้ `client-new-payment-ui` และตาราง `100-user rollout grid`

1. ใน LaunchDarkly เปิด `Client New Payment UI` → `Targeting`
2. แก้ Default rule/Fallthrough ให้เป็น Percentage rollout
3. ตั้ง `New UI` = 10% และ `Legacy UI` = 90%
4. Bucket โดย Context kind/attribute เดิม เช่น `user.key` เพื่อให้ assignment คงที่
5. กด `Review and save` แล้วกดยืนยัน
6. Refresh หน้า `http://localhost:8080`
7. เลื่อนดู `100-user rollout grid` และจำนวน `enabled`
8. เปลี่ยนเป็น 50%/50%, บันทึก แล้ว Refresh หน้าอีกครั้ง

สิ่งที่พูด:

> “Percentage rollout ไม่ใช่การสุ่มใหม่ทุกครั้ง Context key เดิมจะอยู่ Bucket เดิม จึงทำให้ผู้ใช้ไม่สลับไปมาระหว่างรุ่น แต่จำนวนในกลุ่มตัวอย่าง 100 คนเป็นค่าประมาณ ไม่จำเป็นต้องเท่ากับ 10 หรือ 50 พอดีใน Live Mode”

หลังจบ ให้คืน Default rule เป็น `Legacy UI` หรือปิด Flag เพื่อกลับ Safe Baseline ห้ามทิ้ง Percentage rollout ไว้โดยไม่ตั้งใจ

### ช่วงที่ 8 — ปิดการนำเสนอ (นาที 18–20)

1. ในแอปชี้ที่ `Decision timeline`, source และ Trace ID อีกครั้ง
2. กดแท็บ `Architecture`
3. สรุปสามประเด็น:

   - Central governance: Backend ใช้ typed DTM boundary เดียว
   - Progressive delivery: target รายคน, Segment, rollout และ migration stage ได้
   - Safe failure: ทุก Flag มี typed fallback และ Kill Switch

ประโยคสรุป:

> “คุณค่าของตัวอย่างนี้ไม่ใช่แค่เปิด-ปิด UI แต่คือการควบคุมการเปลี่ยนแปลงทั้งเส้นทางแบบค่อยเป็นค่อยไป เห็นเหตุผลของทุก decision และถอยกลับสู่ค่าเดิมได้โดยไม่ทำให้ Control Plane กลายเป็นจุดล้มเหลวของ Banking flow”

## 4. Cleanup หลัง Live Demo

ทำทันทีหลังจบ เพื่อให้ครั้งต่อไปเริ่มจากสถานะที่คาดเดาได้

1. ใน Environment `Devolopment` ปิด Flag ทั้งหกตัว:

   - `client-new-payment-ui`
   - `profile-response-v2`
   - `fraud-engine-version`
   - `payment-api-migration`
   - `payment-v2-enabled`
   - `notification-provider`

2. คืน Rule `Pilot Customers` ของ `payment-api-migration` เป็น `shadow`
3. คืน Default rule ของ `client-new-payment-ui` เป็น `Legacy UI` หากแก้ Percentage rollout
4. ลบ Temporary target ที่เพิ่มระหว่างการซ้อม ถ้ามี
5. Refresh แอป เลือก Narin และตรวจว่าเป็น `LEGACY UI`
6. อย่าแก้ Environment `Production`

หากต้องการหยุดระบบ:

```powershell
docker compose down
```

คำสั่งนี้หยุดและลบ Container/Network ของ Compose แต่ไม่ลบ Docker image จึงเปิดใหม่ด้วย `docker compose up -d` ได้

## 5. Mock Mode — แผนสำรองเมื่อ Live Mode ใช้ไม่ได้

Mock Mode ไม่ต้องใช้ Internet หรือ LaunchDarkly credential และให้ผล deterministic เหมาะสำหรับซ้อมหรือเดโมสำรอง

### เปลี่ยนเป็น Mock Mode ชั่วคราว

เปิด PowerShell ในโฟลเดอร์โปรเจกต์แล้วรัน:

```powershell
$env:POC_MODE = "mock"
docker compose up -d --force-recreate
Remove-Item Env:POC_MODE
```

จากนั้น Refresh `http://localhost:8080` ต้องเห็น:

- `MOCK MODE`
- `Mock provider ready`
- ปุ่มใน `Presenter controls` ใช้งานได้

### สคริปต์ Mock Mode แบบย่อ

#### A. Reset และ Targeting

1. กด `↻ Reset demo`
2. เลือก Mali แล้วกด `Target individual` → Mali เป็น New UI
3. กด Reset
4. เลือก Somchai แล้วกด `Employee segment` → Somchai เป็น New UI
5. เลือก Mali แล้วกด `Pilot segment` → Mali เป็น New UI

อธิบายว่า Mock provider จำลองลำดับกฎ Individual → Segment → Percentage → Safe default แบบ deterministic

#### B. Rollout

1. กด `0%`, `10%`, `50%`, `100%` ตามลำดับ
2. ดูจำนวนใน `100-user rollout grid`
3. กดเปอร์เซ็นต์เดิมซ้ำเพื่อชี้ว่า user เดิมไม่เปลี่ยน Bucket

ใน Mock Mode ชุดผู้ใช้ 100 คนนี้ควรให้ค่าคงที่ประมาณ 0, 9, 44 และ 100 enabled ตามลำดับ

#### C. Payment Migration

1. กด `Shadow`
2. เลือก Mali แล้วกด `Review & send`
3. ตรวจ `v1 authoritative; called v1 + v2; matched`
4. กด `Live` → `New simulation` → `Review & send`
5. ตรวจ `v2 authoritative; called v1 + v2; matched`
6. กด `Complete` → `New simulation` → `Review & send`
7. ตรวจ `v2 authoritative; called v2; not-compared`

#### D. Kill Switch

1. ขณะ Migration เป็น Live หรือ Complete กด `Activate kill switch`
2. กด `New simulation` → `Review & send`
3. ตรวจว่า v1 เป็น authoritative และมี `v2-disabled-by-kill-switch`
4. กด `Deactivate kill switch` เพื่อคืนสถานะ

#### E. Failure และ Fallback

ทดสอบทีละกรณี และกด `Restore all services` ก่อนเปลี่ยนกรณี:

1. `Simulate LD failure`
   - Flag evaluation ใช้ `sdk-default`
   - ระบบกลับ Profile legacy, Fraud v1, Payment v1, Notification provider A
2. `Simulate DTM failure`
   - Domain services ติดต่อ DTM ไม่ได้และใช้ `service-fallback`
3. `Simulate DTM timeout`
   - DTM ช้ากว่า timeout ของ service; local fallback และ circuit breaker ป้องกันการรอต่อเนื่อง
4. `Payment v2 failure`
   - ตั้ง Migration เป็น `Complete` ก่อน
   - รายการต้อง fallback เป็น v1 และมี `v2-failed-safe-fallback`
5. `Provider B failure`
   - เปิด `Pilot segment` หรือ `Target individual` ให้ Mali ก่อน
   - Notification ต้องกลับ `provider-a` และแสดง `queued-after-provider-b-failure`

ข้อควรจำ: `Restore all services` จะ Reset targeting, rollout, migration และ failure simulation ทั้งหมด ไม่ได้คืนเฉพาะ service failure

### กลับ Live Mode หลังใช้ Mock Mode

เนื่องจากตัวแปร PowerShell ถูกลบแล้ว Compose จะกลับไปอ่าน `POC_MODE=launchdarkly` จาก `.env` ให้รัน:

```powershell
docker compose up -d --force-recreate
docker compose ps
```

Refresh หน้าเว็บและตรวจว่า Badge กลับเป็น `LAUNCHDARKLY LIVE` พร้อมข้อความ `LaunchDarkly connected`

## 6. Troubleshooting ระหว่างเดโม

### เปิด `http://localhost:8080` ไม่ได้

รัน:

```powershell
docker compose ps
docker compose logs --tail 100 web-gateway
```

ถ้า Container ยังไม่ขึ้น ให้รัน `docker compose up -d` แล้วรอ Healthcheck

### Badge เป็น Live แต่ขึ้น Provider unavailable/Degraded

1. ตรวจ Internet
2. ตรวจว่า `.env` มี `POC_MODE=launchdarkly`, SDK key และ Client-side ID ที่ถูกต้อง โดยอย่าแชร์ค่าบนจอ
3. รัน:

   ```powershell
   docker compose logs --tail 100 dtm-service
   ```

4. ถ้าแก้ `.env` ให้ Recreate Container:

   ```powershell
   docker compose up -d --force-recreate
   ```

5. ถ้ายังแก้ไม่ทัน ให้เปลี่ยนเป็น Mock Mode และบอกผู้ชมว่าเป็น deterministic offline provider ที่รักษา use case เดิม

### เปิด Flag แล้ว UI ไม่เปลี่ยน

1. ตรวจว่าแก้ Environment `Devolopment`
2. ตรวจว่า `client-new-payment-ui` เปิดให้ Client-side SDK แล้ว
3. รอ 1–3 วินาที
4. สลับ Persona ไปมา
5. Refresh หน้าเว็บ
6. ตรวจข้อความด้านบนว่าคือ `LaunchDarkly connected`

### เปิด Flag ฝั่ง Server แล้ว Timeline ยังเป็นค่าเดิม

1. เลือก Mali เพราะกฎ Server รุ่นใหม่ target ที่ `Pilot Customers`
2. กด `New simulation`
3. กด `Review & send` เพื่อสร้าง evaluation ใหม่
4. ตรวจว่า Flag และ Environment ถูกตัว

### ตาราง rollout ไม่เปลี่ยน

Refresh หน้าเว็บหลังบันทึก Percentage rollout ตารางนี้ไม่ได้อัปเดตด้วย streaming โดยอัตโนมัติ

### เผลอกด Presenter controls ใน Live Mode

ระบบจะปฏิเสธการเขียน Live account โดยตั้งใจ ไม่มี Flag ถูกแก้ ให้ปิด Toast ข้อผิดพลาดและกลับไปใช้ LaunchDarkly Dashboard

### ต้องการดู Log ทุกบริการ

```powershell
docker compose logs --tail 100
```

หลีกเลี่ยงการแสดง Log เต็มจอระหว่างนำเสนอ เพราะอาจมีรายละเอียดระบบที่ไม่จำเป็น แม้ POC นี้จะไม่ใช้ข้อมูลลูกค้าจริงก็ตาม

## 7. คำที่ควรใช้และคำที่ควรหลีกเลี่ยง

ควรพูด:

- “Synthetic payment simulation”
- “Feature delivery/control plane”
- “Safe fallback”
- “v1/v2 authoritative route”
- “Targeting ใช้ synthetic context”
- “Flag ใช้ควบคุม release ไม่ใช่ authorization”

ไม่ควรพูด:

- “เป็นระบบ Banking production-ready แล้ว”
- “Feature Flag ปลอดภัยพอสำหรับใช้แทนสิทธิ์ผู้ใช้”
- “มีการโอนเงินจริง”
- “ระบบไม่มีทางล่ม”
- “เปิด Flag แล้วผู้ใช้ทุกคนจะเปลี่ยนทันที” โดยไม่อธิบาย targeting และ rollout

## 8. Checklist สำหรับพิมพ์หรือเปิดข้างจอ

ก่อนเริ่ม:

- [ ] Docker Desktop พร้อม
- [ ] `docker compose ps` แสดงทุก Service healthy
- [ ] แอปเป็น `LAUNCHDARKLY LIVE`
- [ ] `LaunchDarkly connected`
- [ ] `5/5 healthy`
- [ ] Project = `centrailized-banking`
- [ ] Environment = `devolopment`
- [ ] Flag ทั้งหกตัวเริ่ม Off
- [ ] เปิดหน้าต่าง LaunchDarkly และแอปวางข้างกัน
- [ ] ไม่มี credential หรือข้อมูลส่วนตัวบนจอ

ลำดับเดโม:

- [ ] Safe baseline ด้วย Narin
- [ ] Architecture และ DTM boundary
- [ ] เปิด Client UI Flag
- [ ] Narin = Legacy, Somchai/Mali = New UI
- [ ] เปิด Server Flags ทั้งห้า
- [ ] Mali full journey และ Decision timeline
- [ ] Shadow → Live → Complete
- [ ] Kill Switch กลับ v1
- [ ] Percentage rollout ถ้ามีเวลา
- [ ] สรุป governance, rollout และ fallback

หลังจบ:

- [ ] ปิด Flag ทั้งหกใน Devolopment
- [ ] คืน Payment migration Pilot rule เป็น shadow
- [ ] คืน Client default เป็น Legacy UI
- [ ] ตรวจ Narin เป็น Legacy UI
- [ ] ไม่ได้แก้ Production
