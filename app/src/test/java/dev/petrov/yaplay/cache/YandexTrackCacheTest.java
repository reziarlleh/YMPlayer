package dev.petrov.yaplay.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


import dev.petrov.yaplay.ymusic.YandexMusicClient;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class YandexTrackCacheTest {
    private Context context;
    private YandexTrackCache cache;
    private YandexMusicClient client;
    private YandexMusicClient.Track track;
    private File audio;
    private File cover;
    private byte[] music;
    private byte[] picture;
    private MockedConstruction<MediaExtractor> extractors;

    @Before
    public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        cache = new YandexTrackCache(context);
        cache.clearAllCache();
        track = new YandexMusicClient.Track("42", "7", "42:7", "Song", "Artist", "Album", 2026,
                10000, "https://test.invalid/cover.png", 1);
        cover = YandexTrackCache.likedArtworkFile(context, track.key);
        audio = new File(cover.getParentFile(), cover.getName().replace(".cover", ".mp3"));
        Files.createDirectories(audio.getParentFile().toPath());
        music = new byte[4096];
        music[0] = 'I'; music[1] = 'D'; music[2] = '3';
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff16b7ab);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            bitmap.recycle();
            picture = out.toByteArray();
        }
        client = mock(YandexMusicClient.class);
        when(client.getDirectUrl(eq(track.key), any())).thenReturn("https://test.invalid/audio");
        when(client.downloadBytes(track.coverUrl)).thenReturn(picture);
        doAnswer(call -> {
            Files.write(((File) call.getArgument(1)).toPath(), music);
            return null;
        }).when(client).downloadToFile(anyString(), any(File.class));
        // The native demuxer is simulated here; packet validation has separate tests.
        extractors = mockConstruction(MediaExtractor.class, (extractor, unused) -> {
            AtomicInteger sample = new AtomicInteger();
            MediaFormat format = MediaFormat.createAudioFormat("audio/mpeg", 44100, 2);
            when(extractor.getTrackCount()).thenReturn(1);
            when(extractor.getTrackFormat(0)).thenReturn(format);
            when(extractor.getSampleSize()).thenReturn(512L);
            when(extractor.readSampleData(any(), eq(0))).thenReturn(512);
            when(extractor.getSampleTime()).thenAnswer(call -> sample.get() * 5_000_000L);
            when(extractor.advance()).thenAnswer(call -> sample.incrementAndGet() < 3);
            doAnswer(call -> {
                if (new File((String) call.getArgument(0)).length() < 64) {
                    when(extractor.getTrackCount()).thenReturn(0);
                }
                return null;
            }).when(extractor).setDataSource(anyString());
        });
    }

    @After
    public void cleanup() {
        extractors.close();
        cache.clearAllCache();
    }

    private void seedAudio() throws Exception {
        Files.write(audio.toPath(), music);
        CacheFileIntegrity.remember(audio);
    }

    private void seedCover() throws Exception {
        Files.write(cover.toPath(), picture);
        CacheFileIntegrity.remember(cover);
    }

    @Test
    public void emptyCacheDownloadsMusicAndArtworkInOneOperation() throws Exception {
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertTrue(result.audioDownloaded);
        assertNull(result.audioFailure);
        assertEquals(YandexTrackCache.ArtworkSyncResult.DOWNLOADED, result.artwork);
        assertArrayEquals(music, Files.readAllBytes(audio.toPath()));
        assertArrayEquals(picture, Files.readAllBytes(cover.toPath()));
    }

    @Test
    public void existingAudioOnlyDownloadsMissingArtwork() throws Exception {
        seedAudio();
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertFalse(result.audioDownloaded);
        assertEquals(YandexTrackCache.ArtworkSyncResult.DOWNLOADED, result.artwork);
        verify(client, never()).getDirectUrl(anyString(), any());
        assertArrayEquals(music, Files.readAllBytes(audio.toPath()));
    }

    @Test
    public void healthyPairDoesNotUseNetwork() throws Exception {
        seedAudio(); seedCover();
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertFalse(result.audioDownloaded);
        assertEquals(YandexTrackCache.ArtworkSyncResult.PRESENT, result.artwork);
        verifyNoInteractions(client);
    }

    @Test
    public void existingArtworkOnlyDownloadsMissingAudio() throws Exception {
        seedCover();
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertTrue(result.audioDownloaded);
        assertEquals(YandexTrackCache.ArtworkSyncResult.PRESENT, result.artwork);
        verify(client, never()).downloadBytes(anyString());
    }

    @Test
    public void truncatedDownloadIsNotCommitted() throws Exception {
        music = Arrays.copyOf(music, 12);
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertNotNull(result.audioFailure);
        assertFalse(audio.exists());
        assertFalse(CacheFileIntegrity.checksumFile(audio).exists());
    }

    @Test
    public void invalidRemoteCoverIsNeverStored() throws Exception {
        seedAudio();
        when(client.downloadBytes(track.coverUrl)).thenReturn(Arrays.copyOf(picture, 41));
        assertEquals(YandexTrackCache.ArtworkSyncResult.FAILED, cache.cacheLiked(client, track).artwork);
        assertFalse(cover.exists());
        assertFalse(CacheFileIntegrity.checksumFile(cover).exists());
    }

    @Test
    public void damagedAudioDoesNotRedownloadGoodCover() throws Exception {
        seedAudio(); seedCover();
        byte[] damaged = music.clone(); damaged[500] = 11;
        Files.write(audio.toPath(), damaged);
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertTrue(result.audioDownloaded);
        assertEquals(YandexTrackCache.ArtworkSyncResult.PRESENT, result.artwork);
        verify(client, never()).downloadBytes(anyString());
        assertArrayEquals(music, Files.readAllBytes(audio.toPath()));
    }

    @Test
    public void damagedCoverDoesNotRedownloadGoodAudio() throws Exception {
        seedAudio(); seedCover();
        Files.write(cover.toPath(), Arrays.copyOf(picture, picture.length / 2));
        assertEquals(YandexTrackCache.ArtworkSyncResult.DOWNLOADED, cache.cacheLiked(client, track).artwork);
        verify(client, never()).downloadToFile(anyString(), any());
    }

    @Test
    public void legacyFilesAreValidatedWithoutRedownloading() throws Exception {
        Files.write(audio.toPath(), music);
        Files.write(cover.toPath(), picture);
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertNull(result.audioFailure);
        assertFalse(result.audioDownloaded);
        assertEquals(YandexTrackCache.ArtworkSyncResult.PRESENT, result.artwork);
        assertEquals(CacheFileIntegrity.State.VALID, CacheFileIntegrity.check(audio));
        verifyNoInteractions(client);
    }

    @Test
    public void id3HeaderAloneIsRepaired() throws Exception {
        Files.write(audio.toPath(), Arrays.copyOf(music, 12));
        assertTrue(cache.cacheLiked(client, track).audioDownloaded);
    }

    @Test
    public void failedAudioStillSyncsArtworkAndCanBeRetried() throws Exception {
        doThrow(new IOException("No network")).when(client).downloadToFile(anyString(), any());
        YandexTrackCache.LikedSyncResult result = cache.cacheLiked(client, track);
        assertNotNull(result.audioFailure);
        assertFalse(audio.exists());
        assertEquals(YandexTrackCache.ArtworkSyncResult.DOWNLOADED, result.artwork);
        assertEquals(0, audio.getParentFile().listFiles((dir, name) -> name.endsWith(".tmp")).length);
    }

    @Test
    public void failedRepairPreservesExistingPartsUntilReplacementIsVerified() throws Exception {
        seedAudio(); seedCover();
        byte[] damaged = music.clone(); damaged[500] = 17;
        Files.write(audio.toPath(), damaged);
        doThrow(new IOException("Offline")).when(client).downloadToFile(anyString(), any());
        assertNotNull(cache.cacheLiked(client, track).audioFailure);
        assertArrayEquals(damaged, Files.readAllBytes(audio.toPath()));
        assertArrayEquals(picture, Files.readAllBytes(cover.toPath()));
    }

    @Test
    public void prunesAbandonedTemporaryFilesButNotActiveDownload() throws Exception {
        File old = new File(audio.getParentFile(), audio.getName() + "old.tmp");
        File active = new File(audio.getParentFile(), audio.getName() + "active.tmp");
        Files.write(old.toPath(), music);
        Files.write(active.toPath(), music);
        assertTrue(old.setLastModified(System.currentTimeMillis() - 172_800_000L));
        cache.pruneLikedTracks(Set.of(track.key));
        assertFalse(old.exists());
        assertTrue(active.exists());
    }

    @Test
    public void failedCoverIsRetriedWithoutAudioDownload() throws Exception {
        seedAudio();
        when(client.downloadBytes(track.coverUrl)).thenThrow(new IOException("Offline")).thenReturn(picture);
        assertEquals(YandexTrackCache.ArtworkSyncResult.FAILED, cache.cacheLiked(client, track).artwork);
        assertEquals(YandexTrackCache.ArtworkSyncResult.DOWNLOADED, cache.cacheLiked(client, track).artwork);
        verify(client, never()).downloadToFile(anyString(), any());
    }

    @Test
    public void noArtworkSourceNeverStoresPlaceholder() throws Exception {
        seedAudio();
        track = new YandexMusicClient.Track("42", "7", "42:7", "Song", "Artist", "Album", 2026, 10000, "", 1);
        assertEquals(YandexTrackCache.ArtworkSyncResult.NO_SOURCE, cache.cacheLiked(client, track).artwork);
        assertFalse(cover.exists());
        verifyNoInteractions(client);
    }

    @Test
    public void pruningRetainsGoodPartsOfLikedTrackWithMissingMetadataOrAudio() throws Exception {
        seedAudio(); seedCover();
        assertEquals(0, cache.pruneLikedTracks(Set.of(track.key)));
        assertTrue(audio.exists());
        Files.delete(audio.toPath());
        cache.pruneLikedTracks(Set.of(track.key));
        assertTrue(cover.exists());
        cache.pruneLikedTracks(Set.of());
        assertFalse(cover.exists());
        assertFalse(CacheFileIntegrity.checksumFile(cover).exists());
    }

    @Test
    public void unlikeRemovesMediaAndChecksums() throws Exception {
        seedAudio(); seedCover();
        cache.removeLikedTrack(track.key);
        assertEquals(0, audio.getParentFile().listFiles().length);
    }
}
