package com.monkaydee.tcgcatalogue.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The collector tools that belong to CardNavo Pro. Everything else is free. */
enum class ProFeature { EXPORT_REPORTS, APP_IMPORT, DECKS, MORE_BINDERS, WORTH_GRADING, SET_COST }

/**
 * Who may use the Pro tools (docs/PRO-TIER.md).
 *
 * The paywall is only enforced in builds made with `PRO_PAYWALL=true` (the Play Store build). Every other
 * build, including the APKs on GitHub, leaves all tools open. Scanning, prices, the collection, backups and
 * everything a user has already stored are never behind the paywall.
 */
object Pro {
    /** The one-time product in the Play Console. Its price is set there, never in the app. */
    const val PRODUCT_ID = "cardnavo_pro"

    /** Own binders a free user can have (the main binder is not counted). */
    const val FREE_BINDERS = 1

    enum class Store { IDLE, CONNECTING, READY, UNAVAILABLE }

    /** What to tell the user after they tried to buy or restore. */
    enum class Notice { PURCHASED, PENDING, FAILED, NOTHING_TO_RESTORE, STORE_UNAVAILABLE }

    data class State(
        val enforced: Boolean = false,
        val owned: Boolean = false,
        /** Paid with a slow method (cash, bank transfer): not unlocked until Google confirms the payment. */
        val pending: Boolean = false,
        /** The price as Google Play shows it to this user, e.g. "24,99 €". */
        val price: String? = null,
        val store: Store = Store.IDLE,
        val notice: Notice? = null,
    ) {
        val unlocked: Boolean get() = !enforced || owned
        fun allows(@Suppress("UNUSED_PARAMETER") feature: ProFeature): Boolean = unlocked
        fun canCreateBinder(ownBinders: Int): Boolean = unlocked || ownBinders < FREE_BINDERS
    }

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    fun update(change: (State) -> State) = mutable.update(change)

    /** One purchase as the store reports it, reduced to what the decision needs. */
    data class Bought(val products: List<String>, val paid: Boolean, val pending: Boolean, val acknowledged: Boolean, val token: String = "")

    data class Ownership(val owned: Boolean, val pending: Boolean, val toAcknowledge: List<Bought>)

    /**
     * Decides ownership from what the store returned. When the store answered ([storeAnswered]), its list is
     * the truth, so a refunded purchase locks again. When it could not be reached (offline, no Play Store),
     * the last known answer ([remembered]) stands, so a buyer keeps Pro without a connection.
     * A pending payment never unlocks. Paid purchases must be acknowledged or Google refunds them after 3 days.
     */
    fun resolve(remembered: Boolean, storeAnswered: Boolean, purchases: List<Bought>): Ownership {
        if (!storeAnswered) return Ownership(remembered, pending = false, toAcknowledge = emptyList())
        val mine = purchases.filter { PRODUCT_ID in it.products }
        val paid = mine.filter { it.paid }
        return Ownership(
            owned = paid.isNotEmpty(),
            pending = paid.isEmpty() && mine.any { it.pending },
            toAcknowledge = paid.filter { !it.acknowledged },
        )
    }
}
