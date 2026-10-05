package com.monkaydee.tcgcatalogue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CollectionMigrationTest {
    @Test fun versionSixUpgradePreservesOwnedAndSoldCards() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("tcg-catalogue.db")
        val schema = JSONObject(File("schemas/com.monkaydee.tcgcatalogue.data.db.AppDatabase/6.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase("tcg-catalogue.db", 0, null).use { sql ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i); val table = e.getString("tableName")
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.getJSONArray("indices")
                for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            sql.execSQL("INSERT INTO owned_cards(id,game,cardId,variant,variantLabel,name,number,setId,setName,quantity,condition,priceCurrency,addedAt,language) VALUES (1,'POKEMON','test','normal','Normal','Test','1','s','Set',2,'NM','USD',1,'JA')")
            sql.execSQL("INSERT INTO sold_cards(id,game,cardId,variant,variantLabel,name,number,setName,quantity,condition,purchaseCurrency,salePrice,saleCurrency,soldAt) VALUES (1,'POKEMON','test','normal','Normal','Test','1','Set',1,'NM','USD',25,'USD',1)")
            sql.execSQL("INSERT INTO owned_cards(id,game,cardId,variant,variantLabel,name,number,setId,setName,quantity,condition,priceCurrency,addedAt,language,grader,grade,certNumber) VALUES (2,'POKEMON','test','normal','Normal','Test','1','s','Set',1,'PSA 10','USD',1,'JA','PSA','10','123456')")
            sql.version = 6
        }
        val upgraded = AppDatabase.create(context)
        try {
            assertEquals("JA", upgraded.cards().get(1)!!.language)
            assertEquals(2, upgraded.cards().get(1)!!.quantity)
            val sold = upgraded.sold().getAll().single()
            assertNull(sold.totalBasis); assertEquals(0.0, sold.saleFees, 0.0)
            assertTrue(upgraded.tools().lots().isEmpty())
            assertEquals("cert:123456", upgraded.cards().get(2)!!.copyKey)
            val card = upgraded.cards().get(2)!!
            val newId = upgraded.cards().addOrIncrement(card.copy(id = 0, certNumber = "987654", copyKey = "cert:987654"))
            assertNotEquals(card.id, newId)
            assertEquals(3, upgraded.cards().getAll().size)
            com.monkaydee.tcgcatalogue.ui.AppStrings.init(context)
            try { upgraded.cards().addOrIncrement(card.copy(id = 0)); fail("Duplicate physical certificate must not increment quantity") }
            catch (_: IllegalStateException) { }
            assertEquals(1, upgraded.cards().get(2)!!.quantity)
        } finally { upgraded.close(); context.deleteDatabase("tcg-catalogue.db") }
    }
}
