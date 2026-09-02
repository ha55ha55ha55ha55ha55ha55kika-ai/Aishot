package com.example.screenai

import android.content.Context
import android.util.Base64

object KeyManager {

    private val ENCODED = arrayOf(
        // 10m
        "10m:d1VwaVo1c0tQNDl4",
        "10m:MWsxRFoxMUNaMURz",
        "10m:T0V2ZmVlb1pXMGR0",
        "10m:dHB2bFRBbDIzVERy",
        "10m:ZVdPeUpkWHpTMmtI",
        "10m:bmN5WUR1OG9PSDhV",
        "10m:ajFsZGpUYXJmYm1o",
        "10m:RnUySklIdWQzR3BF",
        "10m:UHl5THg4SFJOSnNC",
        "10m:SW1TMnlGRkdSRGZm",
        "10m:THFUVnk5TWhGZTNR",
        "10m:UHRMYnJJT0U3d3lG",
        "10m:WXF5ZWtrUEU0cmdW",
        "10m:NnlKNHJpdldDUGpr",
        "10m:QXNqZ3ZDV2s5U0pJ",
        "10m:dmtPVUZoM3RkZXl1",
        "10m:Z0V0Um5nUUpzR3Ex",
        "10m:Z1Bzb3JtVk9oMXR1",
        "10m:UkhTS1podlhxQWtY",
        "10m:djBwTVY2Q0dzNTJw",
        "10m:YnVod05EdWhHSlpE",
        "10m:dmRBb2dORm5jaW96",
        "10m:RkJmTGJZZ0dyZEJW",
        "10m:VTBJbFBzc0JyM2dC",
        "10m:UU5kRnFnMWRLUXJt",
        "10m:V2pWUVVmeVc5aVlx",
        "10m:dW1wOHE3dFVRaWNK",
        "10m:cGMzSUVsRzB6Y1Rn",
        "10m:bXVWUThrRDVCNTht",
        "10m:YWpoSWdMSVhHdHNP",
        "10m:YlRlcTk2OGxPaGhn",
        "10m:WkY3ZFA3Wjh6Y2tF",
        "10m:VzVtOVZkTjk5cUFD",
        "10m:TWZLSVBiYjJwbDIx",
        "10m:RU9PUllzbmx3RGU3",
        "10m:UkdmUkFCb1BBVDZ6",
        "10m:cGNTcTR0dlh3YU04",
        "10m:ZE80eThQT1pIVXF0",
        "10m:eFJSZFR2ZUZNSm04",
        "10m:QndSUWxMZ1U3Mnd0",
        // 1h
        "1h:YUJZaElVZmpMMnVy",
        "1h:TDZOMmpWWTFQenFP",
        "1h:NGRpNHdOSkFrT25Q",
        "1h:M3NQQnVxbHh5dkhv",
        "1h:MWdNYkF2Y080a29r",
        "1h:bzQ0TTNPcDZRWXdS",
        "1h:OXlwNXB0NXVKOVBp",
        "1h:cFd0QnR4ZVl3RnV5",
        "1h:OTFUSWp6ZGFydlpV",
        "1h:aTRnaUhWR1hZRUFH",
        "1h:UmNZWWVPd3d3Mk5V",
        "1h:eTRVRUk4ZzRrb0VT",
        "1h:VXVUVno0RTh2ckRC",
        "1h:ZzVIdk9nUjFxcHFJ",
        "1h:RkRRdkpHOXJGTjlX",
        "1h:NDl1Q0JuUU11Y1NO",
        "1h:VHpvbUlKalVyY1FM",
        "1h:VUpJaVNFOUxhNWJR",
        "1h:cEJOUUZuMm1jb0xO",
        "1h:RkNZRnRVaFBoN2pD",
        "1h:M1lqQ0RLMFJ4MHRS",
        "1h:V1VlZFViUGVlMGE2",
        "1h:S2pmUFVZdWlBZ3Ew",
        "1h:SVBSQlFFOE1jY0x5",
        "1h:ODlDUUZmSnN2d3Vp",
        "1h:dU5PMVpONjhpaGwz",
        "1h:eFZpakZoaGhzM21N",
        "1h:cldFNGJEc2psOEhC",
        "1h:MHlwWmJQOTh4SDRv",
        "1h:T1BiRDFDeEJVQk9T",
        "1h:Mk85cURTczk3Tktk",
        "1h:d0J3NVhtVkJveXJJ",
        "1h:eGdEUFNybU40bkww",
        "1h:TnFFSllITFpNZklF",
        "1h:U3pJSlgwWnhrVUJI",
        "1h:aFJLSkpUeFRoaU1L",
        "1h:d2cwQ0R0MnBZRWE5",
        "1h:Mk5wdHc1Z1ZPWjdY",
        "1h:bVFiWUc5YmtlV2F5",
        "1h:b1R0NjhFdzNEeHdS",
        // 1d
        "1d:cnlnR0tjd0NlY1VL",
        "1d:anpNSURpRnhLQm9D",
        "1d:MzVBS3J6eFRiMGNB",
        "1d:cGwzTkZlcXA2VnFR",
        "1d:c2c1WHNmS3VqWldZ",
        "1d:Ykl2dUtKak1hc2hy",
        "1d:b3RuSk1iZ2ZCVzRm",
        "1d:TzQ1anU1YVdqWXB1",
        "1d:WDlnckZ2bWk5TmVn",
        "1d:S3FLRnFkR0J0bzhp",
        "1d:Y0p5Z0FqWDRyaENx",
        "1d:Q2dlQUtSYmVXWDhZ",
        "1d:RmZxZGJIYTZ4RmdX",
        "1d:NFlYdnBSaFFlUDJu",
        "1d:RG9BS001QlcyUzI4",
        "1d:MlNacER5RDhlSnpm",
        "1d:YjVzNnhqVHhvZUNw",
        "1d:akpyME1NeUhnbk9D",
        "1d:ek1CUEI1OGV1c2lT",
        "1d:NWZBZ2RMdFJoa1Zk",
        "1d:MXduYjYybTVLRWhm",
        "1d:SllNY2FrdGh1Smkz",
        "1d:cm5NRlZYUGxKS2dx",
        "1d:blhtUEZieVJRd00y",
        "1d:QkxRRVpNMVZYb3R0",
        "1d:OWxBUEFwUjZBMWpN",
        "1d:U3dkdExGdTZvNDIx",
        "1d:TFMyRjVPUExPMFFq",
        "1d:cmo1YWpMVjI2QTNh",
        "1d:Nm9ZQ0RaaVp3UkV1",
        "1d:dkZkZXVxSkxRSkRj",
        "1d:b0I3VUczUWVJWWtR",
        "1d:aWk0REpsYjJmVTd2",
        "1d:RjkzREdqOXB2UU1p",
        "1d:SG9lZkk3bldPRzVB",
        "1d:dHRnVjhqanlWZldq",
        "1d:cldpckY4RHJhTWh5",
        "1d:VEo0eUJxVGUxQnBS",
        "1d:N1FJYXgwMjM0QVNn",
        "1d:SDJFajV4YjhZeUVZ",
        // 30d
        "30d:NXpmdExKZkp4YnJt",
        "30d:QWM0UEZ4OGRzU25M",
        "30d:dVd5dHRJWW12UzJu",
        "30d:N21CUHZ1eFc2ZnBx",
        "30d:OU82UGNQTlpnVnlK",
        "30d:cEM5d3poOGN0Y1BR",
        "30d:SUI3U0x6ZUd4WXJX",
        "30d:SEF4RmowWXZ0VFFQ",
        "30d:bmdHQXVkNjJzd2pS",
        "30d:RzF1NW1xbzBlNzYy",
        "30d:b0gwdGtXZEZDblR6",
        "30d:TDc0eWdHdmVRY0lX",
        "30d:WnhHc1dBVU5jUm5m",
        "30d:cU5SRjVjdmFvck9H",
        "30d:dXVIN0ZGcHhtbUcw",
        "30d:OVBtZTZvRW1rRDk5",
        "30d:NnpGeUlnbFE0aDV4",
        "30d:TGR6RkU4MjFXcFdW",
        "30d:QkZrYjQ1OVVmckRY",
        "30d:bXdhdXRKTE1OUmdN",
        "30d:MU5oWDlzNXViaW5V",
        "30d:cE05UlpHdElYazNB",
        "30d:SmdBNEZqdGxjY3lh",
        "30d:UFNvaTdhT1I3S0R1",
        "30d:UXZFUEtYZ0dpTHJL",
        "30d:djN0RnFJSmRhcFhL",
        "30d:OTZNU0kydzYxN2ZE",
        "30d:a29hVm5qc1VUeW56",
        "30d:Z1JaZHRMN2x2YkxC",
        "30d:VXVPbVF0WVdPMnpp",
        "30d:UmdHTlZoSk1KWHBH",
        "30d:WG1UcEtVdUxtNTRZ",
        "30d:cDVURk5DTGJqa21D",
        "30d:dTdNR2FQOG0wQ2dk",
        "30d:MDN4ZnczQ1Z0cTJk",
        "30d:UlFBcFNZejNmUzZt",
        "30d:UDFvNTdQNDRENHcx",
        "30d:bVVIQ1RqQkZDVWdM",
        "30d:S3EyTDU1MTg4WFpt",
        "30d:TXA3TnJWeE1kZHRG",
        // forever
        "forever:Zmpmd1lWYURoNWxJ",
        "forever:SzNscXFKTE1nUUs2",
        "forever:Z21zcElLdEZWd1Vl",
        "forever:NDdjNDVZMk5PRVhy",
        "forever:OXpwaUM4RWhONXJS",
        "forever:Uk1ZOVhoVUd4cGlB",
        "forever:azJpdlg0bVpRRVkw",
        "forever:RktSbDRhMlhQNExr",
        "forever:bmFQa1dxTW5LQ04x",
        "forever:SFZJa3hNUFFBTzFE",
        "forever:V3E1a2JHYzVkbVlp",
        "forever:Z21RdmdMM1c1eUpS",
        "forever:ZnlneGVEam4zTnhp",
        "forever:TGlsS1N6ZmthR3RZ",
        "forever:d1JHS01aTFV0NVpO",
        "forever:Uk1DN290R3hLM0ox",
        "forever:TFpKZXo1d200M3Jl",
        "forever:aXZLVzRReGpkWjE4",
        "forever:ZkdJeVNBUzNYbE5q",
        "forever:elVMYmFWc2dWQlVj",
        "forever:UUUxRE1WSGgxSEdz",
        "forever:YVVlUHhSU1NaUk11",
        "forever:cDZsZHVPZVNGbXc5",
        "forever:UzNxU1FTMHM5aTBE",
        "forever:cFRiRUhtbTNxSzhj",
        "forever:bWFIMk5YVHBJUnNm",
        "forever:b05nVDJpZDZCQ1l6",
        "forever:aGdsMjZJRmNmbVhJ",
        "forever:YnROVE0zUklUeTUx",
        "forever:NENBcElYbTVtUExL",
        "forever:c0libzh5bHNoYTlN",
        "forever:U1FwZVBkN0ZNaGFo",
        "forever:QTNIZFBkM09EcXNx",
        "forever:Yjk5TVpHc3ZQOGJD",
        "forever:eElMNTBNckhla2FR",
        "forever:aHNJZU1XQ2xiUzd5",
        "forever:RTNENUZFQ0Y0QlFx",
        "forever:Vmh0d3hkbVFLTWtq",
        "forever:NVptS3F4d0d6Z0tM",
        "forever:NUZ2VFlvTnh1QlVu",
    )

    private fun decode(b64: String): String =
        String(Base64.decode(b64, Base64.DEFAULT)).trim()

    private val periodSeconds = mapOf(
        "10m"     to 10 * 60L,
        "1h"      to 60 * 60L,
        "1d"      to 24 * 60 * 60L,
        "30d"     to 30 * 24 * 60 * 60L,
        "forever" to -1L
    )

    fun validate(ctx: Context, input: String): Long? {
        val prefs = ctx.getSharedPreferences("key_store", Context.MODE_PRIVATE)
        val used  = prefs.getStringSet("used", emptySet())!!.toMutableSet()
        val trimmed = input.trim()

        for (entry in ENCODED) {
            val colonIdx = entry.indexOf(':')
            val period = entry.substring(0, colonIdx)
            val b64    = entry.substring(colonIdx + 1)
            val key    = try { decode(b64) } catch (e: Exception) { continue }
            if (key == trimmed) {
                if (used.contains(key)) return null
                used.add(key)
                prefs.edit().putStringSet("used", used).apply()
                return periodSeconds[period] ?: -1L
            }
        }
        return null
    }

    fun activate(ctx: Context, seconds: Long) {
        val expiry = if (seconds == -1L) Long.MAX_VALUE
                     else System.currentTimeMillis() / 1000L + seconds
        ctx.getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)
            .edit().putLong("key_expiry", expiry).apply()
    }

    fun isAccessValid(ctx: Context): Boolean {
        val expiry = ctx.getSharedPreferences("screenai_prefs", Context.MODE_PRIVATE)
            .getLong("key_expiry", 0L)
        if (expiry == 0L) return false
        if (expiry == Long.MAX_VALUE) return true
        return System.currentTimeMillis() / 1000L < expiry
    }

    fun periodLabel(seconds: Long) = when (seconds) {
        10 * 60L           -> "10 минут"
        60 * 60L           -> "1 час"
        24 * 60 * 60L      -> "1 день"
        30 * 24 * 60 * 60L -> "30 дней"
        else               -> "навсегда"
    }
}
