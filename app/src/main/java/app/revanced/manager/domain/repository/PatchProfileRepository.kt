package app.revanced.manager.domain.repository

import app.revanced.manager.data.room.AppDatabase
import app.revanced.manager.data.room.profile.PatchProfileEntity
import app.revanced.manager.data.room.profile.PatchProfilePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

class PatchProfileRepository(
    db: AppDatabase
) {
    private val dao = db.patchProfileDao()

    fun profilesFlow(): Flow<List<PatchProfile>> =
        dao.observeAll().map(List<PatchProfileEntity>::toDomain)

    fun profilesForPackageFlow(packageName: String): Flow<List<PatchProfile>> =
        dao.observeForPackage(packageName).map(List<PatchProfileEntity>::toDomain)

    suspend fun createProfile(
        packageName: String,
        appVersion: String?,
        name: String,
        payload: PatchProfilePayload
    ): PatchProfile {
        val existing = dao.findByPackageAndName(packageName, name)
        if (existing != null) {
            throw DuplicatePatchProfileNameException(packageName, name)
        }
        val entity = PatchProfileEntity(
            uid = AppDatabase.generateUid(),
            packageName = packageName,
            appVersion = appVersion,
            name = name,
            payload = payload,
            createdAt = System.currentTimeMillis()
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun deleteProfile(uid: Int) = dao.delete(uid)

    suspend fun deleteProfiles(uids: Collection<Int>) {
        if (uids.isEmpty()) return
        dao.delete(uids.toList())
    }

    suspend fun updateProfile(
        uid: Int,
        packageName: String,
        appVersion: String?,
        name: String,
        payload: PatchProfilePayload
    ): PatchProfile? {
        val existing = dao.get(uid) ?: return null
        val conflicting = dao.findByPackageAndName(packageName, name)
        if (conflicting != null && conflicting.uid != uid) {
            throw DuplicatePatchProfileNameException(packageName, name)
        }
        val entity = existing.copy(
            packageName = packageName,
            appVersion = appVersion,
            name = name,
            payload = payload
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun getProfile(uid: Int): PatchProfile? = dao.get(uid)?.toDomain()

    suspend fun exportProfiles(): List<PatchProfileExportEntry> =
        dao.getAll().map(PatchProfileEntity::toExportEntry)

    suspend fun importProfiles(entries: Collection<PatchProfileExportEntry>): ImportProfilesResult {
        if (entries.isEmpty()) return ImportProfilesResult(0, 0)
        var imported = 0
        var skipped = 0
        for (entry in entries) {
            val existing = dao.findByPackageAndName(entry.packageName, entry.name)
            if (existing != null) {
                skipped++
                continue
            }
            val entity = PatchProfileEntity(
                uid = AppDatabase.generateUid(),
                packageName = entry.packageName,
                appVersion = entry.appVersion,
                name = entry.name,
                payload = entry.payload,
                createdAt = entry.createdAt ?: System.currentTimeMillis()
            )
            dao.upsert(entity)
            imported++
        }
        return ImportProfilesResult(imported, skipped)
    }
}

data class PatchProfile(
    val uid: Int,
    val packageName: String,
    val appVersion: String?,
    val name: String,
    val createdAt: Long,
    val payload: PatchProfilePayload
)

@Serializable
data class PatchProfileExportEntry(
    val name: String,
    val packageName: String,
    val appVersion: String?,
    val createdAt: Long?,
    val payload: PatchProfilePayload
)

data class ImportProfilesResult(
    val imported: Int,
    val skipped: Int
)

private fun PatchProfileEntity.toDomain() = PatchProfile(
    uid = uid,
    packageName = packageName,
    appVersion = appVersion,
    name = name,
    createdAt = createdAt,
    payload = payload
)

class DuplicatePatchProfileNameException(
    val packageName: String,
    val profileName: String
) : IllegalArgumentException("Duplicate patch profile name \"$profileName\" for package $packageName")

private fun List<PatchProfileEntity>.toDomain() = map(PatchProfileEntity::toDomain)

private fun PatchProfileEntity.toExportEntry() = PatchProfileExportEntry(
    name = name,
    packageName = packageName,
    appVersion = appVersion,
    createdAt = createdAt,
    payload = payload
)
