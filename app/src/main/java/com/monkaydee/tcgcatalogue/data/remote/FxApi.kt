package com.monkaydee.tcgcatalogue.data.remote

/** ECB reference rates via frankfurter.dev. */
class FxApi(private val http: Http) {
    suspend fun usdToEur(): Double? =
        http.getJson("https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR")["rates"]["EUR"].dbl()

    /** Units of each display currency per 1 EUR (ECB reference rates), for showing prices only. */
    suspend fun eurRates(): Map<String, Double> {
        val rates = http.getJson("https://api.frankfurter.dev/v1/latest?base=EUR&symbols=" + com.monkaydee.tcgcatalogue.data.Money.DISPLAY.joinToString(","))["rates"]
        return com.monkaydee.tcgcatalogue.data.Money.DISPLAY.mapNotNull { c -> rates[c].dbl()?.takeIf { it.isFinite() && it > 0 }?.let { c to it } }.toMap()
    }
}
