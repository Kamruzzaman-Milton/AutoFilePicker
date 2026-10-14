package com.quickroots.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quickroots.data.model.Shortcut
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupShortcut(
    val name: String,
    val treeUri: String,
    val icon: String,
    val sortOrder: Int,
    val parentIndex: Int? = null
)

class BackupManager(private val context: Context) {

    private val gson = Gson()

    fun exportBackup(shortcuts: List<Shortcut>, destinationUri: Uri): Boolean {
        return try {
            val backupData = shortcuts.map { item ->
                val parentIdx = if (item.parentId != null) {
                    shortcuts.indexOfFirst { it.id == item.parentId }.takeIf { it >= 0 }
                } else null

                BackupShortcut(
                    name = item.name,
                    treeUri = item.treeUri,
                    icon = item.icon,
                    sortOrder = item.sortOrder,
                    parentIndex = parentIdx
                )
            }
            val jsonString = gson.toJson(backupData)

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonString)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importBackup(sourceUri: Uri): List<Shortcut> {
        val shortcuts = mutableListOf<Shortcut>()
        try {
            val content = StringBuilder()
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        content.append(line)
                    }
                }
            }

            val type = object : TypeToken<List<BackupShortcut>>() {}.type
            val backupList: List<BackupShortcut> = gson.fromJson(content.toString(), type) ?: emptyList()

            backupList.forEachIndexed { index, backup ->
                val uri = Uri.parse(backup.treeUri)
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Ignore if permission lost, but still keep record
                }

                shortcuts.add(
                    Shortcut(
                        id = (index + 1).toLong(),
                        name = backup.name,
                        treeUri = backup.treeUri,
                        icon = backup.icon,
                        sortOrder = backup.sortOrder.takeIf { it >= 0 } ?: index,
                        parentId = backup.parentIndex?.let { (it + 1).toLong() }
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return shortcuts
    }
}
