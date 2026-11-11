package com.monkaydee.tcgcatalogue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BinderSlotMigrationTest {
    @Test fun binderCardsFromVersionTenBecomeNumberedPockets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("tcg-catalogue.db")
        val schema = JSONObject(File("schemas/com.monkaydee.tcgcatalogue.data.db.AppDatabase/10.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase("tcg-catalogue.db", 0, null).use { sql ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i); val table = e.getString("tableName")
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.getJSONArray("indices")
                for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            for (id in 1..3) sql.execSQL("INSERT INTO owned_cards(id,game,cardId,variant,variantLabel,name,number,setId,setName,quantity,condition,priceCurrency,addedAt,language,copyKey,forTrade) VALUES ($id,'POKEMON','c$id','normal','Normal','Card $id','$id','s','Set',1,'NM','EUR',1,'EN','',0)")
            sql.execSQL("INSERT INTO binders(id,name,cover,createdAt) VALUES (5,'Holos','galaxy',1),(6,'Other','midnight',2)")
            sql.execSQL("INSERT INTO binder_cards(binderId,cardRowId) VALUES (5,3),(5,1),(6,2)")
            sql.version = 10
        }
        val upgraded = AppDatabase.create(context)
        try {
            assertEquals(listOf(1L to 0, 3L to 1), upgraded.binders().slots(5).map { it.cardRowId to it.slot })
            assertEquals(listOf(2L to 0), upgraded.binders().slots(6).map { it.cardRowId to it.slot })
            assertEquals("Holos", upgraded.binders().get(5)!!.name)
        } finally { upgraded.close(); context.deleteDatabase("tcg-catalogue.db") }
    }
}
