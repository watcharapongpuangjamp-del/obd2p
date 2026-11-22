package com.example.core.protocol

import com.example.core.model.DtcCode
import com.example.core.model.DtcLookupResult
import com.example.core.model.DtcOnlineResource
import com.example.core.model.DtcScanResult
import com.example.core.model.DtcSeverity
import java.util.Locale

data class DtcKnowledgeEntry(
    val code: String,
    val system: String,
    val thaiTitle: String,
    val thaiExplanation: String,
    val englishDescription: String,
    val implicationsThai: String,
    val severity: DtcSeverity,
    val symptomsThai: List<String>,
    val causesThai: List<String>,
    val inspectionStepsThai: List<String>
)

object DtcKnowledgeBase {

    private val DTC_FORMAT_REGEX = Regex("^[PCBU][0-3][0-9A-F]{3}$")

    val entries: Map<String, DtcKnowledgeEntry> = listOf(
        DtcKnowledgeEntry(
            code = "P0171",
            system = "Powertrain (Fuel & Air Metering)",
            thaiTitle = "ส่วนผสมบางเกินไป (System Too Lean Bank 1)",
            thaiExplanation = "กล่อง ECU ตรวจพบว่าอัตราส่วนอากาศต่อน้ำมันมีออกซิเจนมากเกินไป ต้องสั่งฉีดน้ำมันชดเชย (Fuel Trim) เกิน +25%",
            englishDescription = "Fuel Trim System Lean (Bank 1)",
            implicationsThai = "เสี่ยงทำให้ห้องเผาไหม้ร้อนจัด (Overheat), วาล์วไอเสียไหม้, รอบเดินเบาดับ และอัตราเร่งสะดุดขณะแซง",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องยนต์เดินสะดุดตอนเดินเบา", "อัตราเร่งวูบหน่วง", "ไฟ Check Engine โชว์"),
            causesThai = listOf("เซนเซอร์ MAF สกปรก", "ท่อยางแวคคั่มหรือท่อไอดีรั่ว", "ปั๊มติ๊กแรงดันตก", "กรองเบนซินอุดตัน"),
            inspectionStepsThai = listOf(
                "ตรวจเช็คค่า STFT และ LTFT บนแดชบอร์ด (หากบวกเกิน +15% ยืนยันส่วนผสมบาง)",
                "ตรวจสอบท่อแวคคั่มและท่อร่วมไอดีหารอยฉีกขาด",
                "ทำความสะอาดเซนเซอร์ MAF ด้วยสเปรย์เฉพาะทาง"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0172",
            system = "Powertrain (Fuel & Air Metering)",
            thaiTitle = "ส่วนผสมหนาเกินไป (System Too Rich Bank 1)",
            thaiExplanation = "ECU ตรวจพบน้ำมันเชื้อเพลิงหนาเกินไป (LTFT ติดลบหนัก) จึงลดปริมาณการฉีดน้ำมันลงแต่ยังไม่พ้นขีดเตือน",
            englishDescription = "Fuel Trim System Rich (Bank 1)",
            implicationsThai = "สิ้นเปลืองเชื้อเพลิงมาก, หัวเทียนเขม่าดำบอดง่าย และทำให้แคทตาไลติกคอนเวอร์เตอร์เสื่อมสภาพเร็ว",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("กินน้ำมันผิดปกติ", "ควันดำ/กลิ่นน้ำมันดิบจากท่อไอเสีย", "รอบเดินเบาต่ำสั่น"),
            causesThai = listOf("หัวฉีดรั่ว/ค้าง", "เรกูเลเตอร์แรงดันน้ำมันเสีย", "กรองอากาศอุดตัน", "เซนเซอร์ O2 หรือ MAF เพี้ยน"),
            inspectionStepsThai = listOf(
                "ตรวจสอบสภาพไส้กรองอากาศว่าอุดตันหรือไม่",
                "ดูค่า STFT/LTFT หากติดลบมากกว่า -15% ตรวจสอบหัวฉีดรั่วและแรงดันน้ำมัน"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0174",
            system = "Powertrain (Fuel & Air Metering)",
            thaiTitle = "ส่วนผสมบางเกินไป ฝั่งสูบที่ 2 (System Too Lean Bank 2)",
            thaiExplanation = "พบอากาศเกินหรือน้ำมันขาดในฝั่งกระบอกสูบ Bank 2 (มักพบในเครื่องยนต์ V6/V8)",
            englishDescription = "Fuel Trim System Too Lean (Bank 2)",
            implicationsThai = "ความร้อนในห้องเผาไหม้สูงผิดปกติ เสี่ยงเครื่องยนต์เขกหรือลูกสูบเสียหายเมื่อใช้งานหนัก",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("กำลังเครื่องยนต์ตก", "เดินเบาไม่เรียบ"),
            causesThai = listOf("ประเก็นท่อร่วมไอดีรั่ว", "สายแวคคั่มฝั่ง Bank 2 รั่ว", "เซนเซอร์ MAF เสื่อม"),
            inspectionStepsThai = listOf("ตรวจเช็ครอยรั่วสุญญากาศฝั่ง Bank 2 และเช็คแรงดันรางหัวฉีด")
        ),
        DtcKnowledgeEntry(
            code = "P0300",
            system = "Powertrain (Ignition / Misfire)",
            thaiTitle = "การจุดระเบิดผิดพลาดหลายสูบ (Random/Multiple Cylinder Misfire)",
            thaiExplanation = "ตรวจพบกระบอกสูบหลายสูบเกิดการจุดระเบิดไม่สมบูรณ์สลับกัน เสี่ยงต่อความเสียหายของเครื่องยนต์และแคทตาไลติก",
            englishDescription = "Random/Multiple Cylinder Misfire Detected",
            implicationsThai = "น้ำมันดิบที่ไม่ถูกเผาไหม้จะไหลเข้าท่อไอเสียทำให้แคทตาไลติกร้อนจัดจนละลาย เครื่องสั่นรุนแรงและอาจดับกลางทาง",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เครื่องสั่นอย่างรุนแรง", "ไฟรูปเครื่องกระพริบเตือน", "รถไม่มีกำลัง", "ควันไอเสียมีกลิ่นน้ำมันดิบ"),
            causesThai = listOf("คอยล์จุดระเบิดเสื่อม", "หัวเทียนบอด/ห่าง", "หัวฉีดตัน", "แรงดันน้ำมันตกหรือกำลังอัดต่ำ"),
            inspectionStepsThai = listOf(
                "ห้ามขับขี่ด้วยความเร็วสูงหากไฟ Check Engine กระพริบ",
                "ตรวจสอบสภาพหัวเทียน คอยล์จุดระเบิด และสายไฟคอยล์",
                "เช็คแรงดันน้ำมันและท่อแวคคั่มร่วม"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0301",
            system = "Powertrain (Ignition / Misfire)",
            thaiTitle = "จุดระเบิดผิดพลาด สูบที่ 1 (Cylinder 1 Misfire)",
            thaiExplanation = "กล่อง ECU จับจังหวะการหมุนของเพลาข้อเหวี่ยงพบว่ากระบอกสูบที่ 1 ไม่จุดระเบิดตามรอบที่กำหนด",
            englishDescription = "Cylinder 1 Misfire Detected",
            implicationsThai = "กำลังเครื่องยนต์หายไป 1 สูบ กินน้ำมันเพิ่ม และเสี่ยงทำให้อุปกรณ์กรองไอเสียเสียหายหากฝืนใช้งาน",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องสั่นชัดเจนตอนจอดเกียร์ D", "เร่งไม่ออก"),
            causesThai = listOf("คอยล์จุดระเบิดสูบ 1 เสีย", "หัวเทียนสูบ 1 เสื่อม", "หัวฉีดสูบ 1 อุดตัน"),
            inspectionStepsThai = listOf("สลับคอยล์สูบ 1 กับสูบ 2 แล้วสแกนซ้ำเพื่อดูว่ารหัสย้ายไป P0302 หรือไม่")
        ),
        DtcKnowledgeEntry(
            code = "P0302",
            system = "Powertrain (Ignition / Misfire)",
            thaiTitle = "จุดระเบิดผิดพลาด สูบที่ 2 (Cylinder 2 Misfire)",
            thaiExplanation = "พบการเผาไหม้ไม่สมบูรณ์เฉพาะที่กระบอกสูบที่ 2",
            englishDescription = "Cylinder 2 Misfire Detected",
            implicationsThai = "เครื่องยนต์สั่น สิ้นเปลืองน้ำมัน และอาจเกิดความเสียหายต่อแคทตาไลติกคอนเวอร์เตอร์",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องยนต์เดินไม่เต็มสูบ", "สั่นตอนออกตัว"),
            causesThai = listOf("คอยล์จุดระเบิดสูบ 2 เสีย", "หัวเทียนสูบ 2 เสื่อม", "หัวฉีดสูบ 2 บกพร่อง"),
            inspectionStepsThai = listOf("ถอดตรวจหัวเทียนสูบ 2 และทดสอบสลับคอยล์เพื่อยืนยัน")
        ),
        DtcKnowledgeEntry(
            code = "P0303",
            system = "Powertrain (Ignition / Misfire)",
            thaiTitle = "จุดระเบิดผิดพลาด สูบที่ 3 (Cylinder 3 Misfire)",
            thaiExplanation = "พบการจุดระเบิดผิดพลาดเฉพาะที่กระบอกสูบที่ 3",
            englishDescription = "Cylinder 3 Misfire Detected",
            implicationsThai = "รถอืด เครื่องสั่น เสี่ยงแคทตาไลติกร้อนจัดหากขับต่อเนื่องเป็นเวลานาน",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("รอบเดินเบาสั่น", "เร่งแซงไม่ขึ้น"),
            causesThai = listOf("คอยล์จุดระเบิดสูบ 3", "หัวเทียนสูบ 3", "ปลั๊กหัวฉีดสูบ 3 หลวม"),
            inspectionStepsThai = listOf("ตรวจสอบหัวเทียนและคอยล์สูบ 3 พร้อมวัดกำลังอัดหากยังไม่หาย")
        ),
        DtcKnowledgeEntry(
            code = "P0304",
            system = "Powertrain (Ignition / Misfire)",
            thaiTitle = "จุดระเบิดผิดพลาด สูบที่ 4 (Cylinder 4 Misfire)",
            thaiExplanation = "พบการจุดระเบิดผิดพลาดเฉพาะที่กระบอกสูบที่ 4",
            englishDescription = "Cylinder 4 Misfire Detected",
            implicationsThai = "สูญเสียกำลังขับเคลื่อน เกิดการสั่นสะเทือนต่อยางแท่นเครื่องและท่อไอเสีย",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องเดินสะดุด", "ไฟรูปเครื่องติดค้างหรือกระพริบ"),
            causesThai = listOf("คอยล์จุดระเบิดสูบ 4 เสีย", "หัวเทียนสูบ 4 บอด", "หัวฉีดสูบ 4 ตัน"),
            inspectionStepsThai = listOf("ตรวจปลั๊กคอยล์ หัวเทียน และสลับคอยล์สูบ 4 ทดสอบ")
        ),
        DtcKnowledgeEntry(
            code = "P0420",
            system = "Powertrain (Emissions)",
            thaiTitle = "ประสิทธิภาพแคทตาไลติกต่ำกว่าเกณฑ์ (Catalyst Efficiency Below Threshold)",
            thaiExplanation = "ตัวกรองไอเสีย (Catalytic Converter) ฟอกไอเสียได้ไม่ถึงมาตรฐาน โดย ECU เปรียบเทียบสัญญาณจากเซนเซอร์ O2 ตัวหน้ากับตัวหลัง",
            englishDescription = "Catalyst System Efficiency Below Threshold (Bank 1)",
            implicationsThai = "รถยังขับได้ปกติในระยะสั้น แต่ค่าไอเสียเกินมาตรฐาน (อาจไม่ผ่านตรวจสภาพ ตรอ.) และหากไส้แคทแตกอาจอุดตันท่อไอเสีย",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("ไฟรูปเครื่องติดค้าง", "กลิ่นไอเสียเหม็นฉุน", "อัตราเร่งตื้อเล็กน้อย"),
            causesThai = listOf("แคทตาไลติกเสื่อมสภาพตามอายุ", "เซนเซอร์ O2 ตัวล่าง (Downstream) ชำรุด", "ท่อไอเสียรั่วก่อนถึงแคท"),
            inspectionStepsThai = listOf(
                "ตรวจเช็คท่อไอเสียหารอยรั่วบริเวณข้อต่อและประเก็น",
                "ตรวจการตอบสนองของเซนเซอร์ O2 ตัวหน้าและตัวหลัง",
                "หลีกเลี่ยงการใช้โค้ดลบหลอกโดยไม่ตรวจหาสาเหตุของส่วนผสมหนา/Misfire ที่ทำให้แคทพัง"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0401",
            system = "Powertrain (EGR Exhaust Recirculation)",
            thaiTitle = "การไหลเวียนไอเสีย EGR ต่ำเกินไป (EGR Flow Insufficient)",
            thaiExplanation = "วาล์ว EGR หรือท่อทางเดินไอเสียมีเขม่าคราบน้ำมันเกาะอุดตัน ทำให้ไอเสียไม่สามารถวนกลับมาลดอุณหภูมิเผาไหม้ได้ (พบบ่อยใน Revo / D-Max / Triton)",
            englishDescription = "Exhaust Gas Recirculation (EGR) Flow Insufficient Detected",
            implicationsThai = "อุณหภูมิห้องเผาไหม้และก๊าซ NOx สูงขึ้น เครื่องเขกขณะเร่ง และระบบ DPF อาจตันเร็วขึ้นในรถดีเซลรุ่นใหม่",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องยนต์เขกขณะเร่ง", "ควันดำเพิ่มขึ้น", "กินน้ำมัน", "บางรุ่นตัดเข้าโหมดเซฟความเร็ว"),
            causesThai = listOf("คราบเขม่าคาร์บอนตันในวาล์ว EGR", "ท่อแป๊บและคูลเลอร์ EGR อุดตัน", "มอเตอร์ไฟฟ้าคุม EGR ค้าง/เสีย"),
            inspectionStepsThai = listOf(
                "ถอดล้างวาล์ว EGR และท่อทางเดินร่วมไอดีให้สะอาด",
                "ตรวจสอบปลั๊กและค่า MAF ขณะวาล์ว EGR เปิด/ปิด"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0087",
            system = "Powertrain (Common-rail Fuel System)",
            thaiTitle = "แรงดันในรางคอมมอนเรลต่ำเกินไป (Fuel Rail Pressure Too Low)",
            thaiExplanation = "แรงดันน้ำมันในรางหัวฉีดคอมมอนเรลต่ำกว่าค่าที่ ECU สั่งการ เสี่ยงเครื่องยนต์ดับกะทันหัน",
            englishDescription = "Fuel Rail/System Pressure - Too Low",
            implicationsThai = "เครื่องยนต์จะตัดเข้าโหมด Safe Mode เร่งไม่ขึ้น หรือดับกลางอากาศขณะเร่งแซงซึ่งเป็นอันตรายต่อความปลอดภัย",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เครื่องยนต์วูบดับขณะเร่งแซงหรือขึ้นเนิน", "สตาร์ทติดยาก", "เร่งไม่ขึ้นล็อกรอบ"),
            causesThai = listOf("กรองโซล่าดักน้ำอุดตัน", "SCV Valve (Suction Control Valve) ติดขัด", "ปั๊มแรงดันสูงหรือหัวฉีดรั่วไหลกลับสูง"),
            inspectionStepsThai = listOf(
                "เปลี่ยนไส้กรองโซล่าทันทีหากครบระยะหรือมีน้ำปนเปื้อน",
                "ตรวจสอบวาล์ว SCV และวัดปริมาณน้ำมันไหลกลับของหัวฉีด"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0088",
            system = "Powertrain (Common-rail Fuel System)",
            thaiTitle = "แรงดันในรางคอมมอนเรลสูงเกินไป (Fuel Rail Pressure Too High)",
            thaiExplanation = "แรงดันน้ำมันในรางหัวฉีดพุ่งสูงเกินขีดจำกัดความปลอดภัย",
            englishDescription = "Fuel Rail/System Pressure - Too High",
            implicationsThai = "เสี่ยงทำให้ท่อทางเดินแรงดันสูง หัวฉีด หรือซีลปั๊มเสียหายรุนแรง ECU จะสั่งดับเครื่องเพื่อป้องกันระบบ",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เสียงเครื่องเขกดังมาก", "วูบดับเมื่อถอนคันเร่งหรือกดหนัก"),
            causesThai = listOf("SCV Valve ค้างตำแหน่งเปิดสุด", "เซนเซอร์แรงดันราง (FRP) เพี้ยน", "ท่อไหลกลับตัน"),
            inspectionStepsThai = listOf("ตรวจเช็ค SCV Valve และปลั๊กเซนเซอร์แรงดันรางหัวฉีด")
        ),
        DtcKnowledgeEntry(
            code = "P0101",
            system = "Powertrain (Air Induction)",
            thaiTitle = "สัญญาณเซนเซอร์แอร์โฟลว์ผิดช่วง (MAF Sensor Range/Performance)",
            thaiExplanation = "สัญญาณวัดปริมาณอากาศจากแอร์โฟลว์ (MAF) ไม่สอดคล้องกับรอบเครื่องยนต์ แรงดัน MAP และมุมเปิดลิ้นเร่ง",
            englishDescription = "Mass or Volume Air Flow Circuit Range/Performance",
            implicationsThai = "การคำนวณจ่ายน้ำมันผิดพลาด รถอืด เร่งไม่ขึ้น เกียร์อาจเปลี่ยนกระตุก และทำให้ระบบ EGR/DPF รวนตาม",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("รถออกตัวอืด วิ่งไม่ออก", "รอบเดินเบาสวิงขึ้นลง", "สิ้นเปลืองเชื้อเพลิง"),
            causesThai = listOf("เซนเซอร์ MAF มีฝุ่น/คราบน้ำมันเกาะ", "กรองอากาศอุดตันรุนแรง", "ท่อยางกรองอากาศแตกหลัง MAF", "ขั้วปลั๊กหลวม"),
            inspectionStepsThai = listOf(
                "ดูค่า PID 10 (MAF g/s) ตอนเดินเบาควรอยู่ราว 2-5 g/s สำหรับเครื่องเบนซินเล็ก หรือ 6-12 g/s สำหรับดีเซล",
                "ตรวจกรองอากาศ ท่ออากาศ และล้างขดลวดแอร์โฟลว์ด้วยน้ำยาล้าง MAF"
            )
        ),
        DtcKnowledgeEntry(
            code = "P0106",
            system = "Powertrain (Air Induction)",
            thaiTitle = "สัญญาณเซนเซอร์แรงดันไอดี MAP ผิดช่วง (MAP/Baro Range/Performance)",
            thaiExplanation = "ค่าแรงดันท่อร่วมไอดี (PID 0B) ไม่สัมพันธ์กับค่าความกดอากาศบรรยากาศ (PID 33) หรือองศาลิ้นเร่ง",
            englishDescription = "Manifold Absolute Pressure/Barometric Pressure Circuit Range/Performance",
            implicationsThai = "จังหวะจ่ายน้ำมันและองศาไฟจุดระเบิดคลาดเคลื่อน รถเร่งตื้อหรือควันดำในรถเทอร์โบ",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("เดินเบาไม่นิ่ง", "กินน้ำมัน", "เร่งตื้อ"),
            causesThai = listOf("รูรับแรงดันของเซนเซอร์ MAP ตันจากเขม่า", "ท่ออินเตอร์คูลเลอร์รั่ว", "เซนเซอร์ MAP เสีย"),
            inspectionStepsThai = listOf("เปรียบเทียบค่า MAP (PID 0B) ตอนเปิดสวิตช์ดับเครื่องต้องใกล้เคียง 100 kPa และถอดล้างคราบเขม่า")
        ),
        DtcKnowledgeEntry(
            code = "P0113",
            system = "Powertrain (Air Induction)",
            thaiTitle = "สัญญาณเซนเซอร์อุณหภูมิอากาศไอดีสูง/ขาดวงจร (IAT Circuit High)",
            thaiExplanation = "กล่อง ECU อ่านค่าอุณหภูมิอากาศไอดีต่ำสุดขั้ว (เช่น -40°C) เนื่องจากวงจรขาดหรือปลั๊กหลุด",
            englishDescription = "Intake Air Temperature Sensor 1 Circuit High",
            implicationsThai = "ECU จ่ายน้ำมันหนาเกินความจำเป็นตอนอากาศร้อน ทำให้กินน้ำมันเพิ่มขึ้นและรอบเดินเบาสูง",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("กินน้ำมันเพิ่ม", "ไฟรูปเครื่องติด"),
            causesThai = listOf("ลืมเสียบปลั๊กแอร์โฟลว์หลังล้างกรองอากาศ", "สายไฟขาดใน", "ตัวต้านทาน IAT ใน MAF เสีย"),
            inspectionStepsThai = listOf("ตรวจดูค่า PID 0F (IAT) หากค้างที่ -40°C ให้เช็คปลั๊กและสายไฟที่ชุดเซนเซอร์ MAF/IAT")
        ),
        DtcKnowledgeEntry(
            code = "P0118",
            system = "Powertrain (Engine Cooling)",
            thaiTitle = "สัญญาณเซนเซอร์ความร้อนน้ำหล่อเย็นสูงผิดปกติ (ECT Circuit High)",
            thaiExplanation = "วงจรเซนเซอร์วัดอุณหภูมิน้ำหล่อเย็น (ECT) เปิดวงจรหรือปลั๊กหลุด ทำให้ ECU อ่านค่าติดลบ (-40°C)",
            englishDescription = "Engine Coolant Temperature Sensor 1 Circuit High",
            implicationsThai = "พัดลมหม้อน้ำจะหมุนแรงสุดตลอดเวลา สตาร์ทติดยาก และ ECU ไม่รู้ความร้อนจริงซึ่งเสี่ยงหากเครื่องฮีท",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("พัดลมหม้อน้ำหมุนสปีดสูงสุดตลอดเวลา", "เกจ์ความร้อนไม่ขึ้นหรือค้าง", "สตาร์ทติดยาก"),
            causesThai = listOf("ปลั๊กเซนเซอร์ ECT หลุด/หัก", "สายไฟถูกหนูกัดขาด", "ตัวเซนเซอร์ ECT ชำรุด"),
            inspectionStepsThai = listOf("สังเกตค่า PID 05 (Coolant Temp) หากแสดง -40°C ให้ตรวจเช็คปลั๊กที่เสื้อวาล์วน้ำทันที")
        ),
        DtcKnowledgeEntry(
            code = "P0122",
            system = "Powertrain (Throttle Control)",
            thaiTitle = "แรงดันสัญญาณตำแหน่งลิ้นเร่งต่ำ (TPS Circuit Low)",
            thaiExplanation = "สัญญาณจากเซนเซอร์ลิ้นปีกผีเสื้อ (TPS) ต่ำกว่าเกณฑ์ปกติ 0.2V",
            englishDescription = "Throttle/Pedal Position Sensor/Switch A Circuit Low",
            implicationsThai = "รถอาจเร่งไม่ขึ้น คันเร่งไม่ตอบสนอง หรือตัดเข้าสู่โหมด Limp-Home จำกัดความเร็ว",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เหยียบคันเร่งไม่ขึ้น", "รอบเดินเบาผิดปกติ", "ไฟรูปเครื่อง/VSC โชว์"),
            causesThai = listOf("ปลั๊กเรือนลิ้นเร่งหลวมหรือสกปรก", "สายไฟช็อตลงกราวด์", "ตัวเรือนลิ้นเร่งไฟฟ้าเสื่อม"),
            inspectionStepsThai = listOf("เปิดสวิตช์ ON ดูค่า PID 11 (TPS %) ขณะเหยียบคันเร่งสุดแล้วค่อย ๆ ปล่อยว่าต่อเนื่องหรือไม่")
        ),
        DtcKnowledgeEntry(
            code = "P0128",
            system = "Powertrain (Engine Cooling)",
            thaiTitle = "อุณหภูมิน้ำหล่อเย็นต่ำกว่าอุณหภูมิควบคุมวาล์วน้ำ (Thermostat Rationality)",
            thaiExplanation = "เครื่องยนต์ทำงานมานานพอสมควรแต่อุณหภูมิน้ำหล่อเย็นยังไม่ถึงระดับทำงานปกติ (~80-90°C)",
            englishDescription = "Coolant Thermostat (Coolant Temperature Below Thermostat Regulating Temperature)",
            implicationsThai = "เครื่องยนต์ทำงานในสภาวะเย็นเกินไป (Open Loop) กินน้ำมันเชื้อเพลิงเพิ่ม น้ำมันเครื่องเสื่อมไว",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("อุณหภูมิความร้อนขึ้นช้ามากหรือตกเวลาวิ่งทางไกล", "กินน้ำมันมากกว่าปกติ"),
            causesThai = listOf("วาล์วน้ำ (Thermostat) ค้างเปิดตลอดเวลา", "เคยถอดวาล์วน้ำออก", "เซนเซอร์ ECT อ่านค่าต่ำเพี้ยน"),
            inspectionStepsThai = listOf("ติดตามค่า PID 05 ขณะขับต่อเนื่อง 10 นาที หากค้างต่ำกว่า 75°C ควรเปลี่ยนวาล์วน้ำแท้")
        ),
        DtcKnowledgeEntry(
            code = "P0130",
            system = "Powertrain (O2 Sensor / Emissions)",
            thaiTitle = "วงจรเซนเซอร์ออกซิเจน Bank 1 ตัวที่ 1 ขัดข้อง (O2 Sensor B1S1)",
            thaiExplanation = "สัญญาณแรงดันจากเซนเซอร์ O2 ตัวหน้าไม่สวิงหรือหยุดตอบสนอง",
            englishDescription = "O2 Sensor Circuit (Bank 1 Sensor 1)",
            implicationsThai = "ECU ไม่สามารถปรับจูนน้ำมันแบบ Closed-Loop ได้ ทำให้สิ้นเปลืองน้ำมันและมลพิษสูง",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("กินน้ำมันเพิ่มขึ้น", "ท่อไอเสียมีเขม่าดำ"),
            causesThai = listOf("เซนเซอร์ O2 ตัวหน้าเสื่อมสภาพ", "สายไฟ O2 ละลายติดท่อไอเสีย", "ปลั๊กชื้นหรือสกปรก"),
            inspectionStepsThai = listOf("ตรวจสอบสายไฟและปลั๊กใต้ท้องรถว่าขาดหรือช็อตกับท่อไอเสียหรือไม่")
        ),
        DtcKnowledgeEntry(
            code = "P0135",
            system = "Powertrain (O2 Sensor Heater)",
            thaiTitle = "วงจรฮีตเตอร์เซนเซอร์ O2 B1S1 ขัดข้อง (O2 Heater Circuit)",
            thaiExplanation = "ขดลวดทำความร้อนภายในเซนเซอร์ O2 ตัวหน้าขาดหรือกินกระแสผิดปกติ",
            englishDescription = "O2 Sensor Heater Circuit (Bank 1 Sensor 1)",
            implicationsThai = "ตอนสตาร์ทเครื่องเย็นระบบจ่ายน้ำมันจะเข้าสู่โหมดประหยัดช้ากว่าปกติและไฟรูปเครื่องจะโชว์ถาวร",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("ไฟ Check Engine ติดทันทีหลังสตาร์ทเครื่องเย็น"),
            causesThai = listOf("ขดลวดฮีตเตอร์ในตัว O2 ขาด (ต้องเปลี่ยนตัวใหม่)", "ฟิวส์วงจร O2 Heater ขาด"),
            inspectionStepsThai = listOf("เช็คฟิวส์ O2 Heater ในกล่องฟิวส์และวัดค่าความต้านทานขดลวดฮีตเตอร์")
        ),
        DtcKnowledgeEntry(
            code = "P0234",
            system = "Powertrain (Turbocharger / Boost)",
            thaiTitle = "แรงดันบูสต์เทอร์โบเกินกำหนด (Turbo Overboost Condition)",
            thaiExplanation = "แรงดันไอดี (MAP / PID 0B) พุ่งสูงเกินขีดจำกัดที่กล่อง ECU กำหนดไว้",
            englishDescription = "Turbocharger/Supercharger Overboost Condition",
            implicationsThai = "ECU จะตัดกำลังเครื่องยนต์เข้า Limp Mode ทันทีเพื่อป้องกันลูกสูบแตกหรือท่ออินเตอร์หลุด",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เร่งแซงแล้วรถวูบตัดกำลังทันที", "บูสต์ขึ้นสูงผิดปกติก่อนตัด"),
            causesThai = listOf("ครีบแปรผันเทอร์โบ (VGT/VN Turbo) ติดขัด", "โซลินอยด์คุมบูสต์หรือท่อแวคคั่มรั่ว", "ปรับตั้งเวสเกต/ดันรางเกินค่า"),
            inspectionStepsThai = listOf("ตรวจสอบก้านคุมครีบเทอร์โบ VGT ว่าขยับคล่องหรือไม่ และเช็คค่า PID 0B ขณะเร่ง")
        ),
        DtcKnowledgeEntry(
            code = "P0299",
            system = "Powertrain (Turbocharger / Boost)",
            thaiTitle = "แรงดันบูสต์เทอร์โบต่ำกว่ากำหนด (Turbo Underboost)",
            thaiExplanation = "แรงดันบูสต์จากเทอร์โบไม่ถึงค่าเป้าหมายขณะเครื่องยนต์รับโหลด (พบบ่อยในรถกระบะดีเซลเทอร์โบ)",
            englishDescription = "Turbocharger/Supercharger A Underboost Condition",
            implicationsThai = "รถไม่มีแรงเร่ง ควันดำพุ่งขณะกดคันเร่ง และอาจทำให้เขม่าสะสมอุดตัน DPF/EGR เร็วขึ้น",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("รถอืดไม่มีกำลัง", "ได้ยินเสียงลมรั่วฟี้จากห้องเครื่องขณะเร่ง"),
            causesThai = listOf("ท่ออินเตอร์คูลเลอร์แตกหรือเหล็กรัดหลุด", "ท่อแวคคั่มคุมเทอร์โบรั่ว", "เทอร์โบหรือมอเตอร์ VGT เสีย"),
            inspectionStepsThai = listOf("ตรวจท่ออินเตอร์คูลเลอร์ทุกจุดหารอยฉีกหรือคราบน้ำมันซึมและดูค่า Boost (PID 0B)")
        ),
        DtcKnowledgeEntry(
            code = "P0335",
            system = "Powertrain (Ignition / Timing)",
            thaiTitle = "วงจรเซนเซอร์ตำแหน่งเพลาข้อเหวี่ยงขัดข้อง (CKP Sensor A Circuit)",
            thaiExplanation = "ECU ขาดสัญญาณความเร็วรอบและการบอกตำแหน่งลูกสูบจากเซนเซอร์เพลาข้อเหวี่ยง (Crankshaft)",
            englishDescription = "Crankshaft Position Sensor A Circuit",
            implicationsThai = "เครื่องยนต์อาจสตาร์ทไม่ติดเลย หรือดับกลางทางกะทันหันเนื่องจาก ECU ไม่สามารถสั่งจุดระเบิดและฉีดน้ำมันได้",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("สตาร์ทยาวไม่ติด", "เครื่องดับกลางอากาศขณะเครื่องร้อน", "เข็มวัดรอบตกเป็น 0"),
            causesThai = listOf("เซนเซอร์หน้าข้อเหวี่ยง (CKP) เสื่อม", "สายไฟ CKP ขาด/กรอบจากความร้อน", "จานสัญญาณสกปรก"),
            inspectionStepsThai = listOf("สังเกต PID 0C (RPM) ขณะบิดสตาร์ท หากขึ้น 0 RPM ให้ตรวจเช็คปลั๊กและตัวเซนเซอร์ CKP ทันที")
        ),
        DtcKnowledgeEntry(
            code = "P0340",
            system = "Powertrain (Ignition / Timing)",
            thaiTitle = "วงจรเซนเซอร์เพลาลูกเบี้ยวขัดข้อง (CMP Sensor A Circuit)",
            thaiExplanation = "ECU ไม่ได้รับสัญญาณซิงค์จากเซนเซอร์แคมชาร์ฟ (Camshaft Position)",
            englishDescription = "Camshaft Position Sensor A Circuit (Bank 1)",
            implicationsThai = "สตาร์ทติดยากกว่าปกติ ระบบวาล์วแปรผัน (VVT-i / MIVEC) หยุดทำงาน และอาจเกิดการจุดระเบิดเพี้ยน",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("สตาร์ทลากยาวกว่าจะติด", "อัตราเร่งอืดตอนรอบปลาย"),
            causesThai = listOf("เซนเซอร์เพลาลูกเบี้ยวเสีย", "ปลั๊กสายไฟหลวม", "ไทม์มิ่งสายพาน/โซ่ราวลิ้นยืดข้ามฟัน"),
            inspectionStepsThai = listOf("ตรวจปลั๊กเซนเซอร์แคมชาร์ฟด้านบนฝาสูบและระดับน้ำมันเครื่อง")
        ),
        DtcKnowledgeEntry(
            code = "P0442",
            system = "Powertrain (EVAP Emission)",
            thaiTitle = "พบรอยรั่วขนาดเล็กในระบบกักเก็บไอเชื้อเพลิง (EVAP Small Leak)",
            thaiExplanation = "ระบบตรวจจับพบแรงดันในถังน้ำมันเชื้อเพลิงรั่วซึมเล็กน้อย",
            englishDescription = "Evaporative Emission System Leak Detected (Small Leak)",
            implicationsThai = "ไม่ส่งผลต่อสมรรถนะการขับขี่โดยตรง แต่ทำให้มีกลิ่นไอน้ำมันและไฟรูปเครื่องติดค้าง",
            severity = DtcSeverity.LOW,
            symptomsThai = listOf("ไฟ Check Engine โชว์โดยรถยังขับปกติ", "อาจได้กลิ่นไอน้ำมันรอบถัง"),
            causesThai = listOf("ปิดฝาถังน้ำมันไม่สนิทหรือยางฝาถังเสื่อม", "วาล์ว Purge / Vent รั่วซึม", "สายยาง EVAP แตกลายงา"),
            inspectionStepsThai = listOf("ลองบิดฝาถังน้ำมันให้แน่นจนดังคลิกแล้วลบโค้ดทดลองขับ")
        ),
        DtcKnowledgeEntry(
            code = "P0455",
            system = "Powertrain (EVAP Emission)",
            thaiTitle = "พบรอยรั่วขนาดใหญ่ในระบบกักเก็บไอเชื้อเพลิง (EVAP Large Leak)",
            thaiExplanation = "ระบบไม่สามารถสร้างสุญญากาศในถังน้ำมันเชื้อเพลิงได้ตามมาตรฐาน",
            englishDescription = "Evaporative Emission System Leak Detected (Large Leak)",
            implicationsThai = "ไอน้ำมันระเหยออกสู่ภายนอกมากขึ้นและไฟ Check Engine โชว์ถาวร",
            severity = DtcSeverity.LOW,
            symptomsThai = listOf("ไฟรูปเครื่องติด", "อาจได้กลิ่นเบนซินหลังจอดรถ"),
            causesThai = listOf("ลืมปิดฝาถังน้ำมันหลังเติมน้ำมัน", "ท่อยางถังดักไอหลุด", "โซลินอยด์ Purge Valve ค้าง"),
            inspectionStepsThai = listOf("ตรวจเช็คฝาปิดถังน้ำมันและท่อเข้าออกถังดักไอชาร์โคล")
        ),
        DtcKnowledgeEntry(
            code = "P0500",
            system = "Powertrain (Vehicle Speed Sensor)",
            thaiTitle = "สัญญาณเซนเซอร์ความเร็วรถขัดข้อง (Vehicle Speed Sensor A)",
            thaiExplanation = "กล่องเครื่องยนต์ไม่ได้รับสัญญาณความเร็วรถยนต์ (VSS / PID 0D) จากเรือนไมล์หรือกล่อง ABS",
            englishDescription = "Vehicle Speed Sensor A",
            implicationsThai = "เข็มไมล์ไม่ขึ้น เกียร์อัตโนมัติไม่เปลี่ยนตำแหน่งหรือเปลี่ยนผิดจังหวะ และระบบ ABS/Cruise Control หยุดทำงาน",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เข็มวัดความเร็วตกอยู่ที่ 0", "เกียร์กระตุกหรือค้างเกียร์ต่ำ", "ไฟ ABS โชว์ร่วมด้วย"),
            causesThai = listOf("เฟืองหรือเซนเซอร์ความเร็วที่เกียร์เสีย", "เซนเซอร์ล้อ ABS ขาด", "สายไฟขาด/หนูกัด"),
            inspectionStepsThai = listOf("ตรวจดู PID 0D ขณะรถวิ่งและเช็คโค้ดในระบบ ABS เพิ่มเติม")
        ),
        DtcKnowledgeEntry(
            code = "P0700",
            system = "Powertrain (Transmission Control)",
            thaiTitle = "ระบบควบคุมเกียร์อัตโนมัติแจ้งรหัสปัญหา (TCM Requested MIL)",
            thaiExplanation = "กล่องควบคุมเกียร์ (TCM) ตรวจพบความผิดปกติภายในระบบเกียร์และส่งคำสั่งให้กล่องเครื่องยนต์เปิดไฟเตือน",
            englishDescription = "Transmission Control System (MIL Request)",
            implicationsThai = "เกียร์อาจเข้าโหมดฉุกเฉิน (Limp Mode ค้างเกียร์ 3) หรือเปลี่ยนเกียร์กระตุกเพื่อป้องกันชุดคลัตช์พัง",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เปลี่ยนเกียร์กระตุกรุนแรง", "รถเร่งไม่ขึ้นเหมือนออกตัวเกียร์สูง", "ไฟเตือนรูปเกียร์หรือ D กระพริบ"),
            causesThai = listOf("โซลินอยด์เกียร์มีปัญหา", "น้ำมันเกียร์เสื่อมหรือพร่อง", "เซนเซอร์รอบอินพุต/เอาต์พุตเกียร์เสีย"),
            inspectionStepsThai = listOf(
                "ตรวจระดับและสีน้ำมันเกียร์อัตโนมัติทันที",
                "จำเป็นต้องสแกนเจาะจงกล่องเกียร์ (7E9 TCM) เพื่อดูรหัส P07xx เฉพาะจุด"
            )
        ),
        DtcKnowledgeEntry(
            code = "P2002",
            system = "Powertrain (Diesel Particulate Filter)",
            thaiTitle = "ประสิทธิภาพตัวกรองเขม่าดีเซล DPF ต่ำกว่าเกณฑ์ (DPF Efficiency Bank 1)",
            thaiExplanation = "ค่าแรงดันต่างคร่อมหม้อกรอง DPF ผิดปกติ แสดงว่าไส้กรองเขม่าดีเซลอาจตันหรือแตกร้าว",
            englishDescription = "Diesel Particulate Filter Efficiency Below Threshold (Bank 1)",
            implicationsThai = "เครื่องยนต์จะกินน้ำมันมากจากการเผาเขม่าบ่อย หากปล่อยจนตันสนิทรถจะเข้าโหมดจำกัดรอบและสตาร์ทไม่ติด",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("ไฟ DPF หรือไฟเครื่องติด", "รอบเดินเบาสูงบ่อยผิดปกติ", "น้ำมันเครื่องเพิ่มระดับจากดีเซลปน"),
            causesThai = listOf("ขับขี่ระยะสั้นในเมืองต่อเนื่องจนเผาเขม่าไม่จบ", "ท่อแรงดันเซนเซอร์ DPF แตก/ตัน", "ใช้น้ำมันเครื่องผิดเกรด (ต้องใช้ Low SAPS)"),
            inspectionStepsThai = listOf("ตรวจท่อสายยางเซนเซอร์วัดแรงดัน DPF และวิ่งทางไกลคงที่เพื่อช่วยฟื้นฟู DPF")
        ),
        DtcKnowledgeEntry(
            code = "P2463",
            system = "Powertrain (Diesel Particulate Filter)",
            thaiTitle = "ปริมาณเขม่าสะสมใน DPF เกินขีดจำกัด (DPF Soot Accumulation)",
            thaiExplanation = "เขม่าคาร์บอนอุดตันหม้อกรอง DPF สูงเกินระดับที่ระบบจะเผาทำความสะอาดเองได้ขณะขับขี่",
            englishDescription = "Diesel Particulate Filter Restriction - Soot Accumulation",
            implicationsThai = "เครื่องยนต์จะถูกลดทอนกำลัง (Safe Mode) และต้องเข้ารับการสั่งฟื้นฟู DPF (Forced Regen) หรือถอดล้างโดยด่วน",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("ไฟเตือน DPF กระพริบ", "รถอืดเร่งไม่เกิน 2,500 รอบ"),
            causesThai = listOf("เขม่าอุดตันสะสม", "หัวฉีดดีเซลจ่ายหนา", "วาล์ว EGR ค้างเปิด"),
            inspectionStepsThai = listOf("หลีกเลี่ยงการเร่งหนักและนำรถเข้าศูนย์บริการเพื่อสั่ง Forced DPF Regeneration พร้อมตรวจน้ำมันเครื่อง")
        ),
        DtcKnowledgeEntry(
            code = "U0100",
            system = "Network (CAN Bus Communication)",
            thaiTitle = "ขาดการสื่อสารกับกล่องควบคุมเครื่องยนต์ ECM/PCM (Lost Comm with ECM)",
            thaiExplanation = "กล่องควบคุมอื่นในรถ (เช่น TCM เกียร์, ABS, เรือนไมล์) ไม่สามารถสื่อสารผ่านสาย CAN Bus กับกล่องเครื่องยนต์ได้",
            englishDescription = "Lost Communication with Engine Control Module (ECM/PCM)",
            implicationsThai = "ระบบความปลอดภัยหลายระบบ (ABS, ถุงลม, เกียร์) อาจหยุดทำงานพร้อมกัน หรือรถสตาร์ทไม่ติด",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เกียร์ไม่เปลี่ยนหรือเข้า Safe Mode", "ไฟเตือน ABS/ESP โชว์พร้อมกัน", "เรือนไมล์เข็มตก"),
            causesThai = listOf("แรงดันแบตเตอรี่ต่ำผิดปกติ (< 11.5V)", "สาย CAN Bus ขาดหรือช็อต", "ฟิวส์เลี้ยงกล่อง ECU ขาด", "ปลั๊กกล่องชื้น"),
            inspectionStepsThai = listOf(
                "ตรวจเช็คแรงดันแบตเตอรี่ (PID 42 หรือ AT RV) และขั้วแบตเตอรี่ว่าหลวมหรือไม่",
                "เช็คกล่องฟิวส์หลักและขั้วต่อ OBD-II / กล่อง ECM"
            )
        ),
        DtcKnowledgeEntry(
            code = "U0121",
            system = "Network (CAN Bus Communication)",
            thaiTitle = "ขาดการสื่อสารกับกล่องควบคุม ABS (Lost Comm with ABS Module)",
            thaiExplanation = "กล่องเครื่องยนต์หรือเรือนไมล์ไม่ได้รับข้อมูลความเร็วและสถานะจากกล่องระบบเบรก ABS",
            englishDescription = "Lost Communication with Anti-Lock Brake System (ABS) Control Module",
            implicationsThai = "ระบบป้องกันล้อล็อก ABS, EBD และระบบช่วยทรงตัว VSC/ESP จะถูกปิดการทำงานชั่วคราว",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("ไฟ ABS และไฟเบรกมือติดค้างพร้อมกัน", "เข็มไมล์ไม่ขึ้นในบางรุ่น"),
            causesThai = listOf("ฟิวส์ปั๊ม ABS ขาด", "แรงดันไฟแบตเตอรี่ตก", "ปลั๊กกล่อง ABS ออกซิไดซ์หรือหลวม"),
            inspectionStepsThai = listOf("ตรวจฟิวส์ ABS ในห้องเครื่อง แรงดันแบตเตอรี่ และสาย CAN ที่กล่อง ABS")
        ),
        DtcKnowledgeEntry(
            code = "C0035",
            system = "Chassis (Braking & ABS)",
            thaiTitle = "เซนเซอร์ความเร็วล้อหน้าซ้ายขัดข้อง (Left Front Wheel Speed Sensor)",
            thaiExplanation = "กล่อง ABS ไม่ได้รับสัญญาณความเร็วจากล้อหน้าซ้าย ทำให้ระบบป้องกันล้อล็อก (ABS) และกันลื่น (TCS) ปิดการทำงาน",
            englishDescription = "Left Front Wheel Speed Sensor Circuit",
            implicationsThai = "รถยังเบรกได้ตามปกติแต่ไม่มีระบบป้องกันล้อล็อก ABS เมื่อเบรกกะทันหันบนถนนเปียก",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("ไฟ ABS และ Traction Control โชว์", "ระบบเบรกใช้งานได้แบบธรรมดาไม่มี ABS"),
            causesThai = listOf("เซนเซอร์ ABS มีคราบโคลน/เศษเหล็กติด", "สายเซนเซอร์ที่ดุมล้อขาด", "แถบแม่เหล็กลูกปืนล้อชำรุด"),
            inspectionStepsThai = listOf("ตรวจเช็คสายไฟเซนเซอร์หลังซุ้มล้อหน้าซ้ายว่าถูกหินดีดขาดหรือโดนหนูกัดหรือไม่")
        ),
        DtcKnowledgeEntry(
            code = "B1000",
            system = "Body (Airbag / SRS)",
            thaiTitle = "กล่องควบคุมระบบถุงลมนิรภัยขัดข้องภายใน (SRS ECU Malfunction)",
            thaiExplanation = "กล่องควบคุมระบบความปลอดภัย SRS ถุงลมนิรภัยรายงานความผิดปกติภายในหน่วยความจำหรือไฟเลี้ยง",
            englishDescription = "Electronic Control Unit (ECU) Performance Malfunction",
            implicationsThai = "ระบบถุงลมนิรภัยและเข็มขัดนิรภัยดึงกลับอัตโนมัติอาจไม่ทำงานเมื่อเกิดอุบัติเหตุ",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("ไฟเตือนถุงลมนิรภัย Airbag โชว์ค้าง"),
            causesThai = listOf("ไฟกระชากในระบบขณะจั๊มพ์แบต", "กล่อง SRS เสื่อมสภาพ", "เคยเกิดอุบัติเหตุชนแล้วยังไม่รีเซ็ต"),
            inspectionStepsThai = listOf("ตรวจแรงดันแบตเตอรี่และให้ช่างผู้เชี่ยวชาญตรวจสอบระบบ SRS ด้วยเครื่องมือมาตรฐาน")
        )
    ).associateBy { it.code }

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase(Locale.US).replace("\\s+".toRegex(), "")

    fun isValidDtcFormat(raw: String): Boolean =
        DTC_FORMAT_REGEX.matches(normalizeCode(raw))

    fun buildOnlineResources(code: String): List<DtcOnlineResource> {
        val clean = normalizeCode(code).lowercase(Locale.US)
        return listOf(
            DtcOnlineResource(
                label = "OBD-Codes.com ($code)",
                url = "https://www.obd-codes.com/$clean"
            ),
            DtcOnlineResource(
                label = "AutoCodes.com ($code)",
                url = "https://www.autocodes.com/$clean.html"
            )
        )
    }

    fun getOrFallbackEntry(code: String): Pair<DtcKnowledgeEntry, Boolean> {
        val normalized = normalizeCode(code)
        val exact = entries[normalized]
        if (exact != null) return exact to true

        return decodeGenericSaeJ2012(normalized) to false
    }

    fun lookupDtc(rawInput: String, scannedResult: DtcScanResult? = null): Result<DtcLookupResult> {
        val normalized = normalizeCode(rawInput)
        if (!isValidDtcFormat(normalized)) {
            return Result.failure(
                IllegalArgumentException(
                    "รูปแบบรหัส DTC ไม่ถูกต้อง (กรุณาระบุ 5 หลัก เช่น P0171, P0420, U0100, C0035, B1000)"
                )
            )
        }

        val (entry, isExact) = getOrFallbackEntry(normalized)
        val allScanned = if (scannedResult != null) {
            scannedResult.confirmedCodes + scannedResult.pendingCodes + scannedResult.permanentCodes
        } else {
            emptyList()
        }
        val matches = allScanned.filter { it.code.equals(normalized, ignoreCase = true) }

        return Result.success(
            DtcLookupResult(
                code = normalized,
                isExactLocalMatch = isExact,
                system = entry.system,
                thaiTitle = entry.thaiTitle,
                thaiExplanation = entry.thaiExplanation,
                englishDescription = entry.englishDescription,
                implicationsThai = entry.implicationsThai,
                severity = entry.severity,
                symptomsThai = entry.symptomsThai,
                potentialCausesThai = entry.causesThai,
                inspectionStepsThai = entry.inspectionStepsThai,
                onlineResources = buildOnlineResources(normalized),
                matchedScannedDtcs = matches,
                queriedTimestamp = System.currentTimeMillis()
            )
        )
    }

    fun searchSuggestions(query: String, limit: Int = 8): List<DtcKnowledgeEntry> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) {
            return entries.values.take(limit)
        }
        return entries.values.filter { entry ->
            entry.code.lowercase(Locale.US).contains(q) ||
                entry.thaiTitle.lowercase(Locale.US).contains(q) ||
                entry.englishDescription.lowercase(Locale.US).contains(q) ||
                entry.system.lowercase(Locale.US).contains(q)
        }.take(limit)
    }

    fun toDtcCodeModel(
        code: String,
        type: com.example.core.model.DtcType,
        ecuId: String,
        rawHex: String,
        evidenceRef: String
    ): DtcCode {
        val normalized = normalizeCode(code)
        val (entry, isExact) = getOrFallbackEntry(normalized)
        return DtcCode(
            code = normalized,
            type = type,
            ecuId = ecuId,
            system = entry.system,
            thaiTitle = entry.thaiTitle,
            thaiExplanation = entry.thaiExplanation,
            englishDescription = entry.englishDescription,
            severity = entry.severity,
            symptomsThai = entry.symptomsThai,
            potentialCausesThai = entry.causesThai,
            implicationsThai = entry.implicationsThai,
            inspectionStepsThai = entry.inspectionStepsThai,
            onlineResources = buildOnlineResources(normalized),
            isGenericFallback = !isExact,
            rawHex = rawHex,
            evidenceRef = evidenceRef
        )
    }

    private fun decodeGenericSaeJ2012(code: String): DtcKnowledgeEntry {
        val domainChar = code.firstOrNull() ?: 'P'
        val originDigit = code.getOrNull(1) ?: '0'
        val subsystemDigit = code.getOrNull(2) ?: '0'

        val domainLabel = when (domainChar) {
            'P' -> "Powertrain (เครื่องยนต์และระบบส่งกำลัง)"
            'C' -> "Chassis (ช่วงล่าง เบรก ABS และพวงมาลัย)"
            'B' -> "Body (ตัวถัง ถุงลมนิรภัย และระบบไฟฟ้าห้องโดยสาร)"
            'U' -> "Network (ระบบสื่อสาร CAN Bus ระหว่างกล่องควบคุม)"
            else -> "General Vehicle System"
        }

        val originLabel = when (originDigit) {
            '0' -> "SAE/ISO Standard Code (รหัสมาตรฐานกลาง)"
            '1' -> "Manufacturer Specific Code (รหัสเฉพาะค่ายผู้ผลิตรถยนต์)"
            '2' -> if (domainChar == 'P' || domainChar == 'U') "SAE Standard / Extended Code" else "Manufacturer Specific Code"
            else -> "Manufacturer / Reserved Extended Code"
        }

        val subsystemLabel = if (domainChar == 'P') {
            when (subsystemDigit) {
                '0', '1', '2' -> "Fuel & Air Metering / Injector System (ระบบจ่ายน้ำมันและอากาศ)"
                '3' -> "Ignition System or Misfire (ระบบจุดระเบิดหรือการเผาไหม้)"
                '4' -> "Auxiliary Emission Controls (ระบบควบคุมไอเสีย EGR/EVAP/CAT)"
                '5' -> "Vehicle Speed & Idle Control (ระบบควบคุมความเร็วและรอบเดินเบา)"
                '6' -> "Computer & Auxiliary Outputs (วงจรภายในกล่อง ECU และเอาต์พุต)"
                '7', '8', '9' -> "Transmission Control System (ระบบส่งกำลังและเกียร์)"
                else -> "Powertrain Subsystem $subsystemDigit"
            }
        } else {
            domainLabel
        }

        val severity = when {
            domainChar == 'U' -> DtcSeverity.HIGH
            domainChar == 'P' && (subsystemDigit == '3' || subsystemDigit == '7') -> DtcSeverity.HIGH
            domainChar == 'B' && originDigit == '1' -> DtcSeverity.HIGH
            else -> DtcSeverity.MEDIUM
        }

        return DtcKnowledgeEntry(
            code = code,
            system = "$domainLabel — $subsystemLabel",
            thaiTitle = "รหัสข้อผิดพลาด $code ($originLabel)",
            thaiExplanation = "ถอดรหัสตามโครงสร้างมาตรฐาน SAE J2012: อยู่ในกลุ่ม $domainLabel ประเภท $originLabel ส่วนย่อย $subsystemLabel",
            englishDescription = "$originLabel ($domainChar-Series Subsystem $subsystemDigit: $code)",
            implicationsThai = "อาจทำให้ระบบย่อย $subsystemLabel ทำงานไม่สมบูรณ์ ไฟเตือน MIL/Check Engine ติดค้าง หรือลดทอนสมรรถนะเพื่อป้องกันความเสียหาย",
            severity = severity,
            symptomsThai = listOf(
                "ไฟเตือนบนหน้าปัดแสดงผลตามระบบ $domainChar",
                "อาจมีผลต่อการทำงานของระบบ $subsystemLabel"
            ),
            causesThai = listOf(
                "เซนเซอร์หรือแอคชูเอเตอร์ในกลุ่ม $subsystemLabel ส่งสัญญาณผิดพลาด",
                "ขั้วปลั๊กหลวม ออกซิไดซ์ หรือสายไฟในระบบ $domainChar ขาด/ช็อต",
                if (originDigit == '1') "รหัสเฉพาะผู้ผลิต แนะนำตรวจสอบคู่มือซ่อมประจำรุ่นรถร่วมด้วย" else "อุปกรณ์เสื่อมสภาพตามอายุการใช้งาน"
            ),
            inspectionStepsThai = listOf(
                "ตรวจสอบสภาพสายไฟ ปลั๊กเสียบ และฟิวส์ที่เกี่ยวข้องในระบบ $subsystemLabel",
                "ดูค่า Live Data (PIDs) ที่สัมพันธ์กับรหัส $code และเปิดแหล่งข้อมูลออนไลน์ด้านล่างเพื่อเทียบสเปคเฉพาะรุ่น"
            )
        )
    }
}
