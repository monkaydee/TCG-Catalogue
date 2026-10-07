package com.monkaydee.tcgcatalogue.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CatalogueCacheTest {
    @Test fun malformedSuccessfulDownloadCannotReplaceUsableCatalogue() = runBlocking {
        val dir=java.nio.file.Files.createTempDirectory("catalogue-cache").toFile()
        val file=dir.resolve("SEALED_POKEMON.json")
        val valid="""{"items":[],"groups":{}}"""
        file.writeText(valid)
        val http=Http(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("<html>upstream error</html>".toResponseBody()).build()
        }.build())
        try {
            val api=CardIndexApi(http,dir)
            assertEquals(file,api.dailyFile(file.name,0))
            assertEquals(valid,file.readText())
            assertNull(api.dailyFile("JAPANESE_NAME_ALIASES.json",0))
        } finally { dir.deleteRecursively() }
    }
}
