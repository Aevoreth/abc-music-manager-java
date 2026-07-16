package com.aevoreth.abcmm.domain.library;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;

/**
 * Library index access. Implementations live in {@code abcmm-storage}.
 */
public interface SongRepository extends AutoCloseable {

    List<LibrarySong> listLibrarySongs(LibraryFilter filter) throws LibraryException;

    /**
     * Distinct non-blank transcriber values from primary-library songs, sorted.
     */
    List<String> listUniqueTranscribers() throws LibraryException;

    /**
     * Resolves the primary on-disk ABC path for a song, if any {@code SongFile} row exists.
     */
    Optional<Path> resolvePrimaryAbcPath(long songId) throws LibraryException;

    /**
     * Looks up a library song by id, or empty if missing.
     */
    Optional<LibrarySong> findSongById(long songId) throws LibraryException;

    /**
     * Song detail fields for the edit dialog (Python {@code get_song_for_detail}).
     */
    Optional<SongDetailInfo> getSongForDetail(long songId) throws LibraryException;

    /**
     * Updates app-managed Song fields (rating, status, notes, lyrics).
     */
    void updateSongAppMetadata(long songId, SongAppMetadataUpdate update) throws LibraryException;

    /**
     * After Raw ABC save: refresh Song + SongFile metadata from parsed ABC (no new schema).
     */
    void updateSongFromParsedFile(
            long songId,
            Path filePath,
            AbcFileMetadata metadata,
            String fileMtime,
            String fileHash) throws LibraryException;

    /**
     * Unlocked setlists that contain this song (Library Set-column navigation).
     */
    List<SetlistRef> listUnlockedSetlistsContainingSong(long songId) throws LibraryException;

    List<StatusInfo> listStatuses() throws LibraryException;

    List<FolderRuleInfo> listFolderRules() throws LibraryException;

    List<AccountTargetInfo> listAccountTargets() throws LibraryException;

    @Override
    void close();
}
