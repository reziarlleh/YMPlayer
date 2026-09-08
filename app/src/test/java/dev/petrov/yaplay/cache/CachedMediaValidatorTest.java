package dev.petrov.yaplay.cache;

import android.graphics.ImageDecoder;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CachedMediaValidatorTest {
    private MediaExtractor audio(long[] times) {
        MediaExtractor extractor = mock(MediaExtractor.class);
        when(extractor.getTrackCount()).thenReturn(1);
        when(extractor.getTrackFormat(0)).thenReturn(MediaFormat.createAudioFormat("audio/mpeg", 44100, 2));
        when(extractor.getSampleSize()).thenReturn(64L);
        when(extractor.readSampleData(any(), eq(0))).thenReturn(64);
        AtomicInteger cursor = new AtomicInteger();
        when(extractor.getSampleTime()).thenAnswer(call -> times[cursor.get()]);
        when(extractor.advance()).thenAnswer(call -> cursor.incrementAndGet() < times.length);
        return extractor;
    }

    @Test public void acceptsReadablePacketsCoveringTrackDuration() {
        assertTrue(CachedMediaValidator.audio(audio(new long[]{0, 5_000_000, 9_000_000}), 10000));
    }

    @Test public void rejectsTruncatedStreamDespiteCorrectHeader() {
        assertFalse(CachedMediaValidator.audio(audio(new long[]{0, 10_000, 20_000}), 10000));
    }

    @Test public void rejectsUnreadablePacket() {
        MediaExtractor extractor = audio(new long[]{0, 5_000_000, 9_000_000});
        when(extractor.readSampleData(any(), eq(0))).thenReturn(-1);
        assertFalse(CachedMediaValidator.audio(extractor, 10000));
    }

    @Test public void rejectsMissingAudioStream() {
        MediaExtractor extractor = mock(MediaExtractor.class);
        assertFalse(CachedMediaValidator.audio(extractor, 10000));
    }

    @Test public void rejectsTruncatedImageEvenWhenHeaderHasDimensions() throws Exception {
        byte[] png;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff16b7ab);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            bitmap.recycle();
            png = out.toByteArray();
        }
        assertTrue(CachedMediaValidator.artwork(ImageDecoder.createSource(ByteBuffer.wrap(png))));
        assertFalse(CachedMediaValidator.artwork(ImageDecoder.createSource(ByteBuffer.wrap(Arrays.copyOf(png, 41)))));
    }
}
