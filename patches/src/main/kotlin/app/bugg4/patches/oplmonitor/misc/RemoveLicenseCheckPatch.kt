package app.bugg4.patches.oplmonitor.misc

import app.bugg4.patches.oplmonitor.Constants.COMPATIBILITY_OPL_MONITOR
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.logging.Logger

/**
 * The PairIP license check bootstraps itself through a manifest-declared
 * ContentProvider that runs before the app starts:
 *
 * `com.pairip.licensecheck.LicenseContentProvider` -> `LicenseClient.initializeLicenseCheck()`
 *
 * Removing the provider removes the check. Nothing else references these classes:
 * `LicenseActivity` is only ever started by `LicenseClient` itself.
 */
private const val LICENSE_CHECK_PROVIDER_PREFIX = "com.pairip.licensecheck."

@Suppress("unused")
val removeLicenseCheckPatch = resourcePatch(
    name = "Remove license check",
    description = "Removes the startup license check (PairIP). Only needed on devices where " +
        "'Change installer source' cannot work, such as Android 9 and older: there the app " +
        "always performs a full Google Play license verification, which fails for any " +
        "sideloaded (patched) install and redirects to Google Play. " +
        "This grants no entitlements and does not affect purchases or premium features.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_OPL_MONITOR)

    execute {
        var removed = 0

        document("AndroidManifest.xml").use { document ->
            val providers = document.getElementsByTagName("provider")

            buildList {
                for (i in 0 until providers.length) {
                    val provider = providers.item(i) as? Element ?: continue

                    if (provider.getAttribute("android:name").startsWith(LICENSE_CHECK_PROVIDER_PREFIX)) {
                        add(provider)
                    }
                }
            }.forEach { provider ->
                provider.parentNode.removeChild(provider)
                removed++
            }
        }

        if (removed == 0) {
            Logger.getLogger(this::class.java.name).warning(
                "No PairIP license provider found in the manifest, no changes made.",
            )
        }
    }
}
