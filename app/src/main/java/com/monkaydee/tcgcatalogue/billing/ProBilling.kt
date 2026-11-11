package com.monkaydee.tcgcatalogue.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.monkaydee.tcgcatalogue.data.Pro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The CardNavo Pro purchase through Google Play Billing: one non-consumable product ([Pro.PRODUCT_ID]).
 * Only created in builds that enforce the paywall. Google Play keeps the purchase with the user's Google
 * account; the app remembers the last answer so Pro also works offline.
 */
class ProBilling(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var product: ProductDetails? = null

    private val listener = PurchasesUpdatedListener { result, _ ->
        when (result.responseCode) {
            // Refreshing asks the store for everything the account owns, which covers this purchase too.
            BillingResponseCode.OK, BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh(announce = true) }
            BillingResponseCode.USER_CANCELED -> Unit
            else -> Pro.update { it.copy(notice = Pro.Notice.FAILED) }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connects to Google Play and loads the price and what the account already owns. Safe to call again. */
    fun start() {
        Pro.update { it.copy(enforced = true, owned = prefs.getBoolean(OWNED, false)) }
        if (client.isReady) { scope.launch { refresh(announce = false) }; return }
        if (Pro.state.value.store == Pro.Store.CONNECTING) return
        Pro.update { it.copy(store = Pro.Store.CONNECTING) }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    Pro.update { it.copy(store = Pro.Store.READY) }
                    scope.launch { refresh(announce = false); loadPrice() }
                } else Pro.update { it.copy(store = Pro.Store.UNAVAILABLE) }
            }

            override fun onBillingServiceDisconnected() = Pro.update { it.copy(store = Pro.Store.IDLE) }
        })
    }

    private suspend fun loadPrice() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(Pro.PRODUCT_ID).setProductType(ProductType.INAPP).build()),
        ).build()
        val found = suspendCancellableCoroutine<List<ProductDetails>> { c ->
            client.queryProductDetailsAsync(params) { _, result -> if (c.isActive) c.resume(result.productDetailsList) }
        }
        product = found.firstOrNull { it.productId == Pro.PRODUCT_ID }
        Pro.update { it.copy(price = product?.oneTimePurchaseOfferDetails?.formattedPrice) }
    }

    /** Asks Google Play what the account owns. [announce] tells the user the outcome (after buying or restoring). */
    private suspend fun refresh(announce: Boolean, restoring: Boolean = false) {
        val (answered, purchases) = suspendCancellableCoroutine<Pair<Boolean, List<Purchase>>> { c ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { result, list ->
                if (c.isActive) c.resume((result.responseCode == BillingResponseCode.OK) to list)
            }
        }
        val ownership = Pro.resolve(prefs.getBoolean(OWNED, false), answered, purchases.map { it.toBought() })
        if (answered) prefs.edit().putBoolean(OWNED, ownership.owned).apply()
        Pro.update {
            it.copy(
                owned = ownership.owned,
                pending = ownership.pending,
                notice = when {
                    !announce -> it.notice
                    !answered -> Pro.Notice.STORE_UNAVAILABLE
                    ownership.owned -> Pro.Notice.PURCHASED
                    ownership.pending -> Pro.Notice.PENDING
                    restoring -> Pro.Notice.NOTHING_TO_RESTORE
                    else -> it.notice
                },
            )
        }
        ownership.toAcknowledge.forEach { bought ->
            // Not acknowledged this time (offline)? The next refresh lists it again and retries.
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(bought.token).build()) { }
        }
    }

    /** Opens Google Play's purchase sheet. */
    fun buy(activity: Activity) {
        val details = product
        if (!client.isReady || details == null) {
            Pro.update { it.copy(notice = Pro.Notice.STORE_UNAVAILABLE) }
            start()
            return
        }
        val offer = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.oneTimePurchaseOfferDetails?.offerToken?.takeIf { it.isNotEmpty() }?.let { offer.setOfferToken(it) }
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(offer.build())).build())
        if (result.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) scope.launch { refresh(announce = true) }
        else if (result.responseCode != BillingResponseCode.OK && result.responseCode != BillingResponseCode.USER_CANCELED) {
            Pro.update { it.copy(notice = Pro.Notice.FAILED) }
        }
    }

    /** "Restore purchase": for a new phone or a reinstall. */
    fun restore() {
        if (!client.isReady) { Pro.update { it.copy(notice = Pro.Notice.STORE_UNAVAILABLE) }; start(); return }
        scope.launch { refresh(announce = true, restoring = true) }
    }

    private fun Purchase.toBought() = Pro.Bought(
        products = products,
        paid = purchaseState == Purchase.PurchaseState.PURCHASED,
        pending = purchaseState == Purchase.PurchaseState.PENDING,
        acknowledged = isAcknowledged,
        token = purchaseToken,
    )

    companion object {
        private const val OWNED = "owned"

        @Volatile var instance: ProBilling? = null
            private set

        /** Called once at app start; does nothing unless this build enforces the paywall. */
        fun init(context: Context, enforced: Boolean) {
            if (!enforced || instance != null) return
            instance = ProBilling(context).also { it.start() }
        }
    }
}
