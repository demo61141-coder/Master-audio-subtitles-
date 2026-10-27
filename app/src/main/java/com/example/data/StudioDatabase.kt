package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.audio.GlossaryRule
import com.example.audio.SubtitleBlock
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val durationSec: Float,
    val sampleRate: Int,
    val status: String, // "raw", "cleaned", "enhanced", "subtitled"
    val sourceLanguage: String,
    val targetLanguage: String,
    val peakDb: Float,
    val rmsDb: Float,
    val noiseFloorDb: Float,
    val wavFilePath: String,
    val subtitlesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "glossary_entries")
data class GlossaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val spokenVariant: String,
    val canonicalTerm: String,
    val category: String = "Brand/Tech",
    val language: String = "All",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_queue")
data class BatchQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val durationSec: Float,
    val languageCode: String,
    val status: String, // "QUEUED", "PROCESSING", "DONE"
    val subtitleCount: Int = 0,
    val exportedFormat: String = "WAV 16-bit + SRT",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "processing_logs")
data class ProcessingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operation: String,
    val label: String,
    val detail: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Int)

    @Query("SELECT * FROM glossary_entries ORDER BY id DESC")
    fun getAllGlossary(): Flow<List<GlossaryEntryEntity>>

    @Query("SELECT COUNT(*) FROM glossary_entries")
    suspend fun getGlossaryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlossary(entry: GlossaryEntryEntity)

    @Query("DELETE FROM glossary_entries WHERE id = :id")
    suspend fun deleteGlossary(id: Int)

    @Query("SELECT * FROM batch_queue ORDER BY createdAt DESC")
    fun getAllBatchItems(): Flow<List<BatchQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchItem(item: BatchQueueEntity): Long

    @Query("UPDATE batch_queue SET status = :status, subtitleCount = :subtitleCount, exportedFormat = :format WHERE id = :id")
    suspend fun updateBatchItem(id: Int, status: String, subtitleCount: Int, format: String)

    @Query("DELETE FROM batch_queue WHERE id = :id")
    suspend fun deleteBatchItem(id: Int)

    @Query("DELETE FROM batch_queue")
    suspend fun clearBatchQueue()

    @Query("SELECT * FROM processing_logs ORDER BY createdAt DESC LIMIT 80")
    fun getRecentLogs(): Flow<List<ProcessingLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ProcessingLogEntity)
}

@Database(
    entities = [
        ProjectEntity::class,
        GlossaryEntryEntity::class,
        BatchQueueEntity::class,
        ProcessingLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "subcraft_voicelab.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}

class StudioRepository(private val dao: StudioDao) {
    val allProjects: Flow<List<ProjectEntity>> = dao.getAllProjects()
    val allGlossary: Flow<List<GlossaryEntryEntity>> = dao.getAllGlossary()
    val allBatchItems: Flow<List<BatchQueueEntity>> = dao.getAllBatchItems()
    val recentLogs: Flow<List<ProcessingLogEntity>> = dao.getRecentLogs()

    suspend fun ensureDefaultGlossary() {
        if (dao.getGlossaryCount() == 0) {
            val defaults = listOf(
                GlossaryEntryEntity(
                    spokenVariant = "সাবক্রাফট এআই",
                    canonicalTerm = "SubCraft AI Studio",
                    category = "Brand",
                    language = "bn"
                ),
                GlossaryEntryEntity(
                    spokenVariant = "ভয়েসল্যাব",
                    canonicalTerm = "VoiceLab Studio",
                    category = "Brand",
                    language = "bn"
                ),
                GlossaryEntryEntity(
                    spokenVariant = "কুবারনেটিস",
                    canonicalTerm = "Kubernetes",
                    category = "Tech",
                    language = "All"
                ),
                GlossaryEntryEntity(
                    spokenVariant = "পাইটর্চ",
                    canonicalTerm = "PyTorch",
                    category = "Tech",
                    language = "All"
                ),
                GlossaryEntryEntity(
                    spokenVariant = "sub craft",
                    canonicalTerm = "SubCraft AI Studio",
                    category = "Brand",
                    language = "en"
                ),
                GlossaryEntryEntity(
                    spokenVariant = "voice lab",
                    canonicalTerm = "VoiceLab",
                    category = "Brand",
                    language = "en"
                )
            )
            for (d in defaults) {
                dao.insertGlossary(d)
            }
        }
    }

    suspend fun saveProject(project: ProjectEntity): Long = dao.insertProject(project)
    suspend fun deleteProject(id: Int) = dao.deleteProject(id)

    suspend fun addGlossary(spoken: String, canonical: String, category: String, language: String) {
        dao.insertGlossary(
            GlossaryEntryEntity(
                spokenVariant = spoken.trim(),
                canonicalTerm = canonical.trim(),
                category = category.trim().ifBlank { "Custom" },
                language = language
            )
        )
    }

    suspend fun deleteGlossary(id: Int) = dao.deleteGlossary(id)

    suspend fun addBatchItem(fileName: String, durationSec: Float, languageCode: String): Long {
        return dao.insertBatchItem(
            BatchQueueEntity(
                fileName = fileName,
                durationSec = durationSec,
                languageCode = languageCode,
                status = "QUEUED"
            )
        )
    }

    suspend fun updateBatchItem(id: Int, status: String, subtitleCount: Int, format: String) {
        dao.updateBatchItem(id, status, subtitleCount, format)
    }

    suspend fun deleteBatchItem(id: Int) = dao.deleteBatchItem(id)
    suspend fun clearBatchQueue() = dao.clearBatchQueue()

    suspend fun logOperation(operation: String, label: String, detail: String, durationMs: Long = 18L) {
        dao.insertLog(
            ProcessingLogEntity(
                operation = operation,
                label = label,
                detail = detail,
                durationMs = durationMs
            )
        )
    }

    companion object {
        fun serializeSubtitles(blocks: List<SubtitleBlock>): String {
            val arr = JSONArray()
            for (b in blocks) {
                val obj = JSONObject().apply {
                    put("id", b.id)
                    put("index", b.index)
                    put("startSec", b.startSec.toDouble())
                    put("endSec", b.endSec.toDouble())
                    put("originalText", b.originalText)
                    put("translatedText", b.translatedText)
                    put("speaker", b.speaker)
                    put("terms", JSONArray(b.matchedGlossaryTerms))
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        fun deserializeSubtitles(json: String): List<SubtitleBlock> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                val list = mutableListOf<SubtitleBlock>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val termsArr = obj.optJSONArray("terms")
                    val terms = mutableListOf<String>()
                    if (termsArr != null) {
                        for (t in 0 until termsArr.length()) terms.add(termsArr.getString(t))
                    }
                    list.add(
                        SubtitleBlock(
                            id = obj.optString("id", "sub_${i + 1}"),
                            index = obj.optInt("index", i + 1),
                            startSec = obj.optDouble("startSec", 0.0).toFloat(),
                            endSec = obj.optDouble("endSec", 2.0).toFloat(),
                            originalText = obj.optString("originalText", ""),
                            translatedText = obj.optString("translatedText", ""),
                            speaker = obj.optString("speaker", "Speaker 1"),
                            matchedGlossaryTerms = terms
                        )
                    )
                }
                list
            }.getOrDefault(emptyList())
        }

        fun toGlossaryRules(entities: List<GlossaryEntryEntity>): List<GlossaryRule> {
            return entities.map {
                GlossaryRule(
                    id = it.id,
                    spokenVariant = it.spokenVariant,
                    canonicalTerm = it.canonicalTerm,
                    category = it.category,
                    language = it.language
                )
            }
        }
    }
}
