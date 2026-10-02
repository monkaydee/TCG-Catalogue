package com.monkaydee.tcgcatalogue.data.remote

/** ECB reference rates via frankfurter.dev. */
class FxApi(private val http: Http) {
    suspend fun usdToEur(): Double? =
        http.getJson("https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR")["rates"]["EUR"].dbl()
}
