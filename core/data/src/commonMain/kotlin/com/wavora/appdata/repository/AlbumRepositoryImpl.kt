package com.wavora.appdata.repository

import com.wavora.appdata.db.datasource.LocalDataSource
import com.wavora.appdata.extension.getFullDataFromDB
import com.wavora.appdata.mapping.toAlbumEntity
import com.wavora.appdata.mapping.toAlbumsResult
import com.wavora.appdata.parser.parseAlbumData
import com.wavora.domain.model.entities.AlbumEntity
import com.wavora.domain.model.entities.FollowedArtistSingleAndAlbum
import com.wavora.domain.model.model.browse.album.AlbumBrowse
import com.wavora.domain.model.model.searchResult.albums.AlbumsResult
import com.wavora.domain.repository.AlbumRepository
import com.wavora.domain.utils.Resource
import com.wavora.scraper.YouTube
import com.wavora.scraper.models.AlbumItem
import com.wavora.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

private const val TAG = "AlbumRepositoryImpl"

internal class AlbumRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val youTube: YouTube,
) : AlbumRepository {
    override fun getAllAlbums(limit: Int): Flow<List<AlbumEntity>> =
        flow {
            emit(localDataSource.getAllAlbums(limit))
        }.flowOn(Dispatchers.IO)

    override fun getAlbum(id: String): Flow<AlbumEntity?> =
        flow {
            emit(localDataSource.getAlbum(id))
        }.flowOn(Dispatchers.IO)

    override fun getAlbumAsFlow(id: String) = localDataSource.getAlbumAsFlow(id)

    override fun getLikedAlbums(): Flow<List<AlbumEntity>> =
        flow {
            emit(
                getFullDataFromDB { limit, offset ->
                    localDataSource.getLikedAlbums(
                        limit,
                        offset,
                    )
                },
            )
        }.flowOn(Dispatchers.IO)

    override fun insertAlbum(albumEntity: AlbumEntity) =
        flow {
            emit(localDataSource.insertAlbum(albumEntity))
        }.flowOn(Dispatchers.IO)

    override suspend fun updateAlbumLiked(
        albumId: String,
        likeStatus: Int,
    ) = withContext(Dispatchers.IO) {
        localDataSource.updateAlbumLiked(likeStatus, albumId)
        // Локальный лайк альбома -> YouTube Music (target.playlistId = audioPlaylistId).
        // Best-effort: сбой сети не должен ронять UI, поэтому только логируем.
        val audioPlaylistId = localDataSource.getAlbum(albumId)?.audioPlaylistId
        if (!audioPlaylistId.isNullOrBlank()) {
            youTube.setPlaylistLiked(audioPlaylistId, likeStatus == 1).fold(
                onSuccess = { status ->
                    if (status !in 200..299) {
                        Logger.e(TAG, "YouTube Music album like sync failed with HTTP $status")
                    }
                },
                onFailure = { error ->
                    Logger.e(TAG, "YouTube Music album like sync failed: ${error.message ?: error}")
                },
            )
        }
    }

    override suspend fun updateAlbumInLibrary(
        inLibrary: LocalDateTime,
        albumId: String,
    ) = withContext(Dispatchers.IO) { localDataSource.updateAlbumInLibrary(inLibrary, albumId) }

    override suspend fun updateAlbumDownloadState(
        albumId: String,
        downloadState: Int,
    ) = withContext(Dispatchers.IO) {
        localDataSource.updateAlbumDownloadState(
            downloadState,
            albumId,
        )
    }

    override suspend fun insertFollowedArtistSingleAndAlbum(followedArtistSingleAndAlbum: FollowedArtistSingleAndAlbum) =
        withContext(Dispatchers.IO) {
            localDataSource.insertFollowedArtistSingleAndAlbum(followedArtistSingleAndAlbum)
        }

    override suspend fun deleteFollowedArtistSingleAndAlbum(channelId: String) =
        withContext(Dispatchers.IO) {
            localDataSource.deleteFollowedArtistSingleAndAlbum(channelId)
        }

    override suspend fun getAllFollowedArtistSingleAndAlbums(): Flow<List<FollowedArtistSingleAndAlbum>?> =
        flow {
            val list =
                getFullDataFromDB { limit, offset ->
                    localDataSource.getAllFollowedArtistSingleAndAlbums(limit, offset)
                }
            emit(list)
        }.flowOn(Dispatchers.IO)

    override suspend fun getFollowedArtistSingleAndAlbum(channelId: String): Flow<FollowedArtistSingleAndAlbum?> =
        flow {
            emit(localDataSource.getFollowedArtistSingleAndAlbum(channelId))
        }.flowOn(Dispatchers.IO)

    override fun getAlbumData(browseId: String): Flow<Resource<AlbumBrowse>> =
        flow {
            runCatching {
                youTube
                    .album(browseId, withSongs = true)
                    .onSuccess { result ->
                        emit(Resource.Success(parseAlbumData(result)))
                    }.onFailure { e ->
                        Logger.d(TAG, "getAlbumData -> error: ${e.message}")
                        emit(Resource.Error(e.message.toString()))
                    }
            }
        }.flowOn(Dispatchers.IO)

    override fun getAlbumMore(
        browseId: String,
        params: String,
    ): Flow<Pair<String, List<AlbumsResult>>?> =
        flow {
            runCatching {
                youTube
                    .browse(browseId = browseId, params = params)
                    .onSuccess { data ->
                        Logger.w(TAG, "getAlbumMore -> result: $data")
                        val items =
                            (data.items.firstOrNull()?.items ?: emptyList()).mapNotNull { item ->
                                item as? AlbumItem
                            }
                        emit(
                            (data.title ?: "") to (
                                items.map {
                                    it.toAlbumsResult()
                                }
                            ),
                        )
                    }.onFailure {
                        it.printStackTrace()
                        emit(null)
                    }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun syncLikedAlbums(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val remoteAlbums = youTube.getLibraryAlbums().getOrThrow()
                val remoteBrowseIds = remoteAlbums.mapTo(mutableSetOf()) { it.id }
                val localLikedAlbums =
                    getFullDataFromDB { limit, offset ->
                        localDataSource.getLikedAlbums(limit, offset)
                    }

                // Снимаем локальный лайк только если YouTube реально вернул непустую библиотеку:
                // пустой ответ (сбой авторизации, приватный аккаунт) не должен стирать библиотеку.
                if (remoteBrowseIds.isNotEmpty()) {
                    localLikedAlbums
                        .filterNot { it.browseId in remoteBrowseIds }
                        .forEach { album ->
                            Logger.d(TAG, "syncLikedAlbums -> unliked remotely: ${album.browseId}")
                            localDataSource.updateAlbumLiked(0, album.browseId)
                        }
                }

                remoteAlbums.forEach { album ->
                    val existing = localDataSource.getAlbum(album.id)
                    if (existing == null) {
                        // insertAlbum построен на INSERT IGNORE, поэтому вставка не затирает
                        // скачанные треки/время добавления, если строка уже есть.
                        localDataSource.insertAlbum(album.toAlbumEntity())
                    } else if (!existing.liked) {
                        localDataSource.updateAlbumLiked(1, album.id)
                    }
                }
            }
        }
}