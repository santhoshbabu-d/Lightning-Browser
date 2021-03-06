package acr.browser.lightning.adblock.whitelist

import acr.browser.lightning.database.whitelist.AdBlockWhitelistRepository
import acr.browser.lightning.database.whitelist.WhitelistItem
import acr.browser.lightning.utils.domainForUrl
import android.util.Log
import io.reactivex.Completable
import io.reactivex.Scheduler
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * An in memory representation of the ad blocking whitelist. Can be queried synchronously.
 */
@Singleton
class SessionWhitelistModel @Inject constructor(
        private val adBlockWhitelistModel: AdBlockWhitelistRepository,
        @Named("database") private val ioScheduler: Scheduler
) : WhitelistModel {

    private var whitelistSet = hashSetOf<String>()

    init {
        adBlockWhitelistModel
                .allWhitelistItems()
                .map { it.map(WhitelistItem::url).toHashSet() }
                .subscribeOn(ioScheduler)
                .subscribe { hashSet -> whitelistSet = hashSet }
    }

    override fun isUrlWhitelisted(url: String): Boolean = whitelistSet.contains(domainForUrl(url))

    override fun addUrlToWhitelist(url: String) {
        domainForUrl(url)?.let { domain ->
            val whitelistItem = WhitelistItem(domain, System.currentTimeMillis())
            adBlockWhitelistModel
                    .whitelistItemForUrl(domain)
                    .isEmpty
                    .flatMapCompletable {
                        if (it) {
                            adBlockWhitelistModel.addWhitelistItem(whitelistItem)
                        } else {
                            Completable.complete()
                        }
                    }
                    .subscribeOn(ioScheduler)
                    .subscribe { Log.d(TAG, "whitelist item added to database") }

            whitelistSet.add(domain)
        }
    }

    override fun removeUrlFromWhitelist(url: String) {
        domainForUrl(url)?.let { domain ->
            adBlockWhitelistModel
                    .whitelistItemForUrl(domain)
                    .flatMapCompletable(adBlockWhitelistModel::removeWhitelistItem)
                    .subscribeOn(ioScheduler)
                    .subscribe { Log.d(TAG, "whitelist item removed from database") }

            whitelistSet.remove(domain)
        }
    }

    companion object {
        private const val TAG = "SessionWhitelistModel"
    }
}