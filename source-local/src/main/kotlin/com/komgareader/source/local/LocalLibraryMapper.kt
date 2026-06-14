package com.komgareader.source.local

class LocalLibraryMapper {
    fun map(entries: List<ScannedEntry>): LocalIndex {
        val files = entries.filter { !it.isDirectory && formatOf(it.relativePath) != null }
        val grouped: Map<String?, List<ScannedEntry>> = files.groupBy { e ->
            val seg = e.relativePath.substringBefore('/')
            if (seg == e.relativePath) null else seg
        }
        val series = mutableListOf<LocalSeries>()
        grouped.filterKeys { it != null }.forEach { (folder, folderFiles) ->
            val books = folderFiles
                .sortedWith(compareBy(naturalOrder) { it.relativePath })
                .map { it.toBook() }
            if (books.isNotEmpty()) {
                series += LocalSeries(remoteId = folder!!, title = folder, books = books)
            }
        }
        grouped[null].orEmpty().forEach { f ->
            series += LocalSeries(
                remoteId = f.relativePath,
                title = titleOf(f.relativePath),
                books = listOf(f.toBook()),
            )
        }
        return LocalIndex(series.sortedWith(compareBy(naturalOrder) { it.title }))
    }

    private fun ScannedEntry.toBook() = LocalBook(
        remoteId = relativePath,
        title = titleOf(relativePath),
        format = formatOf(relativePath)!!,
    )
}
