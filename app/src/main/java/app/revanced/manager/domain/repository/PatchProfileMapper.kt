package app.revanced.manager.domain.repository

import android.util.Log
import app.revanced.manager.data.room.options.Option
import app.revanced.manager.data.room.profile.PatchProfilePayload
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.patcher.patch.PatchBundle
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import java.security.MessageDigest
data class PatchProfileConfiguration(
    val selection: PatchSelection,
    val options: Options,
    val missingBundles: Set<Int>,
    val changedBundles: Set<Int>
)

fun PatchSelection.toPayload(
    sources: List<PatchBundleSource>,
    bundleInfo: Map<Int, PatchBundleInfo.Global>
): PatchProfilePayload? {
    if (isEmpty()) return null
    val sourceMap = sources.associateBy { it.uid }
    return PatchProfilePayload(
        entries.map { (uid, patches) ->
            val source = sourceMap[uid]
            val info = bundleInfo[uid]
            PatchProfilePayload.Bundle(
                bundleUid = uid,
                patches = patches.map { it.trim() }.filter { it.isNotEmpty() }.sorted(),
                options = emptyMap(),
                displayName = source?.displayTitle ?: info?.name,
                sourceEndpoint = (source as? RemotePatchBundle)?.endpoint,
                sourceName = source?.patchBundle?.manifestAttributes?.name ?: source?.name ?: info?.name,
                version = info?.version
            )
        }
    )
}

fun PatchProfilePayload.remapAndExtractSelection(
    sources: List<PatchBundleSource>,
    signatures: Map<Int, Set<String>>
): Pair<PatchProfilePayload, PatchSelection> {
    val remapped = remapLocalBundles(sources, signatures)
    val selection = remapped.bundles.associate { bundle ->
        val patches = bundle.patches.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        bundle.bundleUid to patches
    }.filterValues { it.isNotEmpty() }
    return remapped to selection
}

fun PatchProfile.toConfiguration(
    bundles: Map<Int, PatchBundleInfo.Scoped>,
    sources: Map<Int, PatchBundleSource>
): PatchProfileConfiguration {
    val selection = mutableMapOf<Int, MutableSet<String>>()
    val options = mutableMapOf<Int, MutableMap<String, MutableMap<String, Any?>>>()
    val missingBundles = mutableSetOf<Int>()
    val changedBundles = mutableSetOf<Int>()
    val endpointToSource = sources.values.mapNotNull { source ->
        (source as? RemotePatchBundle)?.endpoint?.let { endpoint -> endpoint to source }
    }.toMap()

    payload.bundles.forEach { bundle ->
        var resolvedUid = bundle.bundleUid
        var info = bundles[resolvedUid]
        if (info == null) {
            val endpoint = bundle.sourceEndpoint
            if (endpoint != null) {
                val matchingSource = endpointToSource[endpoint]
                if (matchingSource != null) {
                    resolvedUid = matchingSource.uid
                    info = bundles[resolvedUid]
                }
            }
        }
        if (info == null) {
            missingBundles += bundle.bundleUid
            return@forEach
        }
        val patchMetadata = info.patches.associateBy { it.name }

        val selectedPatches = bundle.patches
            .filter { patchMetadata.containsKey(it) }
            .toMutableSet()

        selection[resolvedUid] = selectedPatches

        val bundleOptions = mutableMapOf<String, MutableMap<String, Any?>>()
        var bundleChanged = selectedPatches.size != bundle.patches.size

        bundle.options.forEach { (patchName, optionValues) ->
            val patchInfo = patchMetadata[patchName] ?: run {
                bundleChanged = true
                return@forEach
            }
            val optionMetadata = patchInfo.options?.associateBy { it.key } ?: emptyMap()

            optionValues.forEach { (key, serialized) ->
                val option = optionMetadata[key] ?: run {
                    bundleChanged = true
                    return@forEach
                }
                val value = try {
                    serialized.deserializeFor(option)
                } catch (e: Option.SerializationException) {
                    Log.w(
                        tag,
                        "Failed to deserialize option $name:$patchName:$key for bundle ${bundle.bundleUid}",
                        e
                    )
                    bundleChanged = true
                    return@forEach
                }

                bundleOptions
                    .getOrPut(patchName, ::mutableMapOf)[key] = value
            }
        }

        if (bundleOptions.isNotEmpty()) {
            options[resolvedUid] = bundleOptions
        }

        if (bundleChanged) {
            changedBundles += resolvedUid
        }
    }

    return PatchProfileConfiguration(
        selection = selection.mapValues { it.value.toSet() },
        options = options.mapValues { bundleEntry ->
            bundleEntry.value.mapValues { it.value.toMap() }
        },
        missingBundles = missingBundles,
        changedBundles = changedBundles
    )
}

fun PatchProfilePayload.remapLocalBundles(
    sources: List<PatchBundleSource>,
    signatures: Map<Int, Set<String>> = emptyMap()
): PatchProfilePayload {
    if (bundles.isEmpty()) return this

    val localSources = sources.filter { it.asRemoteOrNull == null }
    val byDisplayTitle = localSources.associateBy { it.displayTitle.trim().lowercase() }
    val byName = localSources.associateBy { it.name.trim().lowercase() }
    val byIdentifier = localSources.mapNotNull { source ->
        val identifier = source.patchBundle?.manifestAttributes?.name
            ?.takeUnless { it.isNullOrBlank() }
            ?: source.name
        identifier.trim().takeIf { it.isNotEmpty() }?.lowercase()?.let { it to source }
    }.toMap()
    val endpointToSource = sources.mapNotNull { source ->
        source.asRemoteOrNull?.endpoint?.let { it to source }
    }.toMap()

    var changed = false

    val resolvedSignatures = signatures.mapValuesTo(mutableMapOf()) { (_, value) ->
        value.map { it.trim().lowercase() }.toSet()
    }
    localSources.forEach { source ->
        if (resolvedSignatures.containsKey(source.uid)) return@forEach
        val bundle = source.patchBundle ?: return@forEach
        val names = runCatching { PatchBundle.Loader.metadata(bundle) }
            .getOrNull()
            ?.map { it.name.trim().lowercase() }
            ?.toSet()
        if (!names.isNullOrEmpty()) resolvedSignatures[source.uid] = names
    }

    val hashIndex = buildMap<String, MutableList<PatchBundleSource>> {
        localSources.forEach { source ->
            val signature = resolvedSignatures[source.uid]
            val hash = signature?.signatureHash()
            if (hash != null) getOrPut(hash, ::mutableListOf).add(source)
        }
    }

    val remappedBundles = bundles.map { bundle ->
        val direct = sources.firstOrNull { it.uid == bundle.bundleUid }
        if (direct != null) {
            if (direct.asRemoteOrNull != null) {
                return@map bundle
            }
            val updated = bundle.copy(
                bundleUid = direct.uid,
                displayName = direct.displayTitle,
                sourceName = direct.patchBundle?.manifestAttributes?.name ?: direct.name,
                sourceEndpoint = null
            )
            if (updated != bundle) changed = true
            return@map updated
        }

        bundle.sourceEndpoint?.let { endpoint ->
            val remote = endpointToSource[endpoint]
            if (remote != null) {
                return@map bundle
            }
        }

        val searchKeys = buildList {
            bundle.sourceName?.trim()?.lowercase()?.let(::add)
            bundle.displayName?.trim()?.lowercase()?.let(::add)
        }
        val target = searchKeys.firstNotNullOfOrNull { key ->
            byDisplayTitle[key] ?: byName[key] ?: byIdentifier[key]
        }
        if (target != null && target.asRemoteOrNull == null) {
            val updated = bundle.copy(
                bundleUid = target.uid,
                displayName = target.displayTitle,
                sourceName = target.patchBundle?.manifestAttributes?.name ?: target.name,
                sourceEndpoint = null
            )
            if (updated != bundle) changed = true
            return@map updated
        }

        val normalizedSignature = bundle.patches.map { it.trim().lowercase() }.toSet()
        val signatureHash = normalizedSignature.signatureHash()
        val signatureMatchByHash = signatureHash
            ?.let(hashIndex::get)
            ?.firstOrNull { candidate ->
                resolvedSignatures[candidate.uid].orEmpty() == normalizedSignature
            }
        val signatureMatch = signatureMatchByHash ?: localSources
            .mapNotNull { source ->
                val signature = resolvedSignatures[source.uid].orEmpty()
                if (normalizedSignature.isNotEmpty() && normalizedSignature.all(signature::contains)) {
                    val extra = signature.size - normalizedSignature.size
                    source to extra
                } else null
            }
            .minByOrNull { it.second }
            ?.first

        if (signatureMatch != null) {
            val updated = bundle.copy(
                bundleUid = signatureMatch.uid,
                displayName = signatureMatch.displayTitle,
                sourceName = signatureMatch.patchBundle?.manifestAttributes?.name ?: signatureMatch.name,
                sourceEndpoint = null
            )
            if (updated != bundle) changed = true
            updated
        } else bundle
    }

    return if (changed) copy(bundles = remappedBundles) else this
}

fun Map<Int, PatchBundleInfo.Global>.toSignatureMap() =
    mapValues { (_, info) ->
        info.patches.map { it.name.trim().lowercase() }.toSet()
    }

private fun Collection<String>.signatureHash(): String? {
    if (isEmpty()) return null
    val digest = MessageDigest.getInstance("SHA-256")
    this.sorted().forEach { value ->
        val bytes = value.toByteArray()
        digest.update(bytes)
        digest.update(0)
    }
    return digest.digest().joinToString(separator = "") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

