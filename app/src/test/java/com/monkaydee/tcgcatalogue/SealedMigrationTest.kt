package com.monkaydee.tcgcatalogue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.monkaydee.tcgcatalogue.data.db.AppDatabase
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.Money
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class SealedMigrationTest {
    @Test fun upgradePreservesOldItemsAndSeparatesLanguageVariants() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("tcg-catalogue.db")
        val schema=JSONObject(File("schemas/com.monkaydee.tcgcatalogue.data.db.AppDatabase/7.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase("tcg-catalogue.db",0,null).use { db ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val name=e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",name))
                val indices=e.getJSONArray("indices")
                for(j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
            }
            db.execSQL("INSERT INTO sealed_items(id,game,productId,name,groupName,quantity,price,priceCurrency,purchasePrice,addedAt) VALUES (1,'POKEMON',123,'Box','Set',2,100,'USD',80,1)")
            db.version=7
        }
        val db=AppDatabase.create(context)
        try {
            val old=db.sealed().find(Game.POKEMON,123,"EN")!!
            assertEquals(2,old.quantity);assertEquals(100.0,old.price!!,0.0);assertEquals(80.0,old.purchasePrice!!,0.0)
            assertEquals("EN",old.language);assertNull(old.referencePrice)
            db.sealed().upsert(old.copy(id=0,language="JA",price=null,referencePrice=150.0,referenceCurrency="EUR"))
            assertEquals(2,db.sealed().getAll().size)
            val japanese=db.sealed().find(Game.POKEMON,123,"JA")!!
            assertNotEquals(old.id,japanese.id)
            assertEquals(0.0,Money.sealedValue(japanese,"EUR",.9),0.0)
        } finally { db.close();context.deleteDatabase("tcg-catalogue.db") }
    }
}
