/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.os.Build
import android.os.DeadObjectException
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemotePieceList
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle
import me.him188.ani.app.domain.torrent.callback.ITorrentFileEntryStatsCallback
import me.him188.ani.app.domain.torrent.client.ConnectivityAware
import me.him188.ani.app.domain.torrent.cont.ContTorrentFileEntryGetInputParams
import me.him188.ani.app.domain.torrent.cont.ContTorrentFileEntryResolveFile
import me.him188.ani.app.domain.torrent.parcel.PTorrentFileEntryStats
import me.him188.ani.app.domain.torrent.parcel.PTorrentInputParameter
import me.him188.ani.app.domain.torrent.parcel.toRemoteContinuationException
import me.him188.ani.app.torrent.anitorrent.session.AnitorrentDownloadSession
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.utils.coroutines.CancellationException
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.absolutePath
import kotlin.coroutines.CoroutineContext

class TorrentFileEntryProxy(
    private val delegate: TorrentFileEntry,
    private val connectivityAware: ConnectivityAware,
    context: CoroutineContext
) : IRemoteTorrentFileEntry.Stub() {
    private val scope = context.childScope()

    override fun getFileStats(flow: ITorrentFileEntryStatsCallback?): IDisposableHandle {
        val job = scope.launch(Dispatchers.IO_) {
            delegate.fileStats.collect {
                if (!connectivityAware.isConnected) return@collect

                try {
                    flow?.onEmit(PTorrentFileEntryStats(it.downloadedBytes, it.downloadProgress))
                } catch (doe: DeadObjectException) {
                    throw CancellationException("Cancelled collecting file entry stats.", doe)
                }
            }
        }

        return DisposableHandleProxy { job.cancel() }
    }

    override fun getLength(): Long {
        return delegate.length
    }

    override fun getFileName(): String {
        return delegate.fileName
    }

    override fun getPathInTorrent(): String {
        return delegate.pathInTorrent
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun getPieces(): IRemotePieceList {
        return PieceListProxy(delegate.pieces, scope.coroutineContext)
    }

    override fun getSupportsStreaming(): Boolean {
        return delegate.supportsStreaming
    }

    override fun createHandle(): IRemoteTorrentFileHandle {
        return TorrentFileHandleProxy(delegate.createHandle(), connectivityAware, scope.coroutineContext)
    }

    override fun resolveFile(cont: ContTorrentFileEntryResolveFile?): IDisposableHandle? {
        if (cont == null) return null

        val job = scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                if (!connectivityAware.isConnected) return@CoroutineExceptionHandler
                cont.resumeWithException(throwable.toRemoteContinuationException())
            } + Dispatchers.IO_,
        ) {
            val result = delegate.resolveFile().absolutePath
            if (!connectivityAware.isConnected) return@launch
            cont.resume(result)
        }

        return DisposableHandleProxy { job.cancel() }
    }

    override fun resolveFileMaybeEmptyOrNull(): String? {
        return delegate.resolveFileMaybeEmptyOrNull()?.absolutePath
    }

    override fun getTorrentInputParams(cont: ContTorrentFileEntryGetInputParams?): IDisposableHandle? {
        if (cont == null) return null
        if (delegate !is AnitorrentDownloadSession.AnitorrentEntry) {
            val exception = IllegalStateException("Expected delegate instance is AnitorrentEntry, actual $delegate")
            cont.resumeWithException(exception.toRemoteContinuationException())
            throw exception
        }

        val job = scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                if (!connectivityAware.isConnected) return@CoroutineExceptionHandler
                cont.resumeWithException(throwable.toRemoteContinuationException())
            } + Dispatchers.IO_,
        ) {
            val result = delegate.createTorrentInputParameters()
            if (!connectivityAware.isConnected) return@launch
            cont.resume(
                PTorrentInputParameter(
                    file = result.file.absolutePath,
                    logicalStartOffset = result.logicalStartOffset,
                    bufferSize = result.bufferSize,
                    size = result.size,
                ),
            )
        }

        return DisposableHandleProxy { job.cancel() }
    }

    override fun torrentInputOnWait(pieceIndex: Int) {
        check(delegate is AnitorrentDownloadSession.AnitorrentEntry) {
            "Expected delegate instance is AnitorrentEntry, actual $delegate"
        }

        delegate.updatePieceDeadlinesForSeek(delegate.pieces.getByPieceIndex(pieceIndex))
    }
}