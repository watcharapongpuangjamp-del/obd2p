package com.example.core.model

data class VehicleProfile(
    val id: String,
    val make: String,
    val model: String,
    val year: String,
    val engine: String,
    val fuelType: String,
    val protocol: String,
    val descriptionThai: String,
    val vin: String,
    val defaultDtcScenario: List<String> = emptyList()
) {
    companion object {
        val THAI_PRESETS = listOf(
            VehicleProfile(
                id = "toyota_revo_28",
                make = "Toyota",
                model = "Hilux Revo 2.8 GD Super Flex",
                year = "2022",
                engine = "1GD-FTV 2.8L VN-Turbo Diesel",
                fuelType = "Diesel (B7/B10/B20)",
                protocol = "ISO 15765-4 (CAN 11/500)",
                descriptionThai = "รถกระบะดีเซลคอมมอนเรลยอดนิยมในไทย (ตรวจจับระบบ DPF, หัวฉีด, แรงดันเทอร์โบ)",
                vin = "MHFBA3BB8N0123456",
                defaultDtcScenario = listOf("P0401", "P0087") // EGR Flow Insufficient, Fuel Rail Pressure Low
            ),
            VehicleProfile(
                id = "isuzu_dmax_19",
                make = "Isuzu",
                model = "D-Max 1.9 Ddi BluePower",
                year = "2023",
                engine = "RZ4E-TC 1.9L Turbo Diesel",
                fuelType = "Diesel",
                protocol = "ISO 15765-4 (CAN 11/500)",
                descriptionThai = "รถกระบะประหยัดน้ำมันยอดฮิต (ตรวจวัดแรงดันเทอร์โบ, ความร้อนหม้อน้ำ, EGR)",
                vin = "MP1TFR87JNT789012",
                defaultDtcScenario = listOf("P0101") // Mass Air Flow Range
            ),
            VehicleProfile(
                id = "honda_civic_fc",
                make = "Honda",
                model = "Civic FC 1.5 VTEC Turbo",
                year = "2021",
                engine = "L15B7 1.5L Turbo Gasoline",
                fuelType = "Gasoline (Gasohol 95/E20)",
                protocol = "ISO 15765-4 (CAN 11/500)",
                descriptionThai = "รถเก๋งเทอร์โบเบนซิน (ตรวจจับ Misfire, Fuel Trim, อุณหภูมิไอดี)",
                vin = "MRHFC1680MT456789",
                defaultDtcScenario = listOf("P0171", "P0300") // System Too Lean, Random Misfire
            ),
            VehicleProfile(
                id = "toyota_yaris_ativ",
                make = "Toyota",
                model = "Yaris Ativ 1.2 Dual VVT-iE",
                year = "2023",
                engine = "3NR-FKE 1.2L Eco Car",
                fuelType = "Gasoline (E20)",
                protocol = "ISO 15765-4 (CAN 11/500)",
                descriptionThai = "อีโคคาร์ยอดนิยม (ตรวจวัดออกซิเจนเซนเซอร์, แคทตาไลติก, อัตราสิ้นเปลือง)",
                vin = "MHFK51B38P0654321",
                defaultDtcScenario = listOf("P0420") // Catalyst System Efficiency Below Threshold
            ),
            VehicleProfile(
                id = "byd_atto_3",
                make = "BYD",
                model = "Atto 3 Extended Range",
                year = "2023",
                engine = "150kW Permanent Magnet Synchronous EV",
                fuelType = "BEV (Blade Battery 60.48kWh)",
                protocol = "ISO 15765-4 (CAN 29/500)",
                descriptionThai = "รถยนต์ไฟฟ้า 100% (ตรวจวัดแรงดันไฟแบตเตอรี่ 12V และ High-Voltage Gateway)",
                vin = "LC0CE4CE0P1112233",
                defaultDtcScenario = listOf("U0100") // Lost Communication with ECM/PCM
            )
        )
    }
}
