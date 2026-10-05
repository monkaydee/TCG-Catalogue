package com.monkaydee.tcgcatalogue
import com.monkaydee.tcgcatalogue.data.CostLedger
import com.monkaydee.tcgcatalogue.data.CollectionExport
import com.monkaydee.tcgcatalogue.data.db.CostLot
import org.junit.Assert.*
import org.junit.Test
class CostLedgerTest {
 @Test fun convertingRawCopyToSlabRequiresActualGradingFeeBeforeShowingProfit() {
  val card = com.monkaydee.tcgcatalogue.data.db.OwnedCard(id=1,game=com.monkaydee.tcgcatalogue.data.db.Game.POKEMON,cardId="x",variant="normal",variantLabel="Normal",name="Test",number="1",setId="s",setName="Set",price=100.0,grader="PSA",grade="10",condition="PSA 10")
  val rawLot = CostLot(cardRowId=1,quantity=1,purchase=20.0,grading=0.0,shipping=0.0,tax=0.0,currency="EUR")
  assertNull(CostLedger.pnl(card,listOf(CostLedger.afterGrading(rawLot)),"EUR",0.9))
  val paid = rawLot.copy(grading=25.0)
  assertEquals(45.0,CostLedger.pnl(card,listOf(CostLedger.afterGrading(paid)),"EUR",0.9)!!,0.001)
  assertEquals("EUR",CostLedger.afterGrading(paid).currency)
 }
 @Test fun pnlRequiresKnownQuoteAndCompleteCostCoverage() {
  val card = com.monkaydee.tcgcatalogue.data.db.OwnedCard(id=1,game=com.monkaydee.tcgcatalogue.data.db.Game.POKEMON,cardId="x",variant="normal",variantLabel="Normal",name="Test",number="1",setId="s",setName="Set",quantity=2,price=25.0)
  val lot = CostLot(cardRowId=1,quantity=2,purchase=10.0,grading=5.0,shipping=2.0,tax=1.0,currency="USD")
  assertEquals(14.0,CostLedger.pnl(card,listOf(lot),"USD",0.9)!!,0.0)
  assertNull(CostLedger.pnl(card.copy(price=null),listOf(lot),"USD",0.9))
  assertNull(CostLedger.pnl(card,listOf(lot.copy(quantity=1)),"USD",0.9))
  assertNull(CostLedger.pnl(card,listOf(lot.copy(grading=null)),"USD",0.9))
 }
 @Test fun learningNormalizationRetainsNativeText() {
  assertEquals("svp123ピカチュウ",com.monkaydee.tcgcatalogue.data.SharedLearning.normalizeRead("ＳＶＰ １２３ • ピカチュウ"))
 }
 @Test fun costsIncludeGradingShippingAndTaxAndKeepCurrency() {
  val lot=CostLot(cardRowId=1,quantity=2,purchase=100.0,grading=25.0,shipping=5.0,tax=2.0,currency="USD")
  assertEquals(264.0,CostLedger.basis(lot,"USD",0.9)!!,0.001)
  assertEquals(237.6,CostLedger.basis(lot,"EUR",0.9)!!,0.001)
  assertEquals("USD",lot.currency)
 }
 @Test fun unknownCostsDifferFromZeroCosts() {
  val zero=CostLot(cardRowId=1,quantity=1,purchase=0.0,grading=0.0,shipping=0.0,tax=0.0,currency="EUR")
  assertEquals(0.0,CostLedger.basis(zero,"EUR",0.9)!!,0.0)
  assertNull(CostLedger.basis(zero.copy(grading=null),"EUR",0.9))
 }
 @Test fun partialSalesAllocateOldestPurchaseWithoutChangingOtherCopies() {
  val old=CostLot(id="a",cardRowId=1,quantity=2,purchase=10.0,currency="USD",acquiredAt=1)
  val recent=old.copy(id="b",quantity=3,purchase=20.0,acquiredAt=2)
  val(sold,kept)=CostLedger.allocate(listOf(recent,old),3)
  assertEquals(listOf(2,1),sold.map{it.quantity});assertEquals(listOf(10.0,20.0),sold.map{it.purchase})
  assertEquals(2,kept.single().quantity);assertEquals(20.0,kept.single().purchase!!,0.0)
 }
 @Test fun exportedCellsCannotExecuteSpreadsheetFormulas() {
  assertEquals("\"'=SUM(1)\"",CollectionExport.cell("=SUM(1)"))
  assertEquals("\"a,b\"",CollectionExport.cell("a,b"));assertEquals("\"\"",CollectionExport.cell(null))
 }
}
