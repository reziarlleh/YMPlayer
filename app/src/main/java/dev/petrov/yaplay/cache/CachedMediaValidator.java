package dev.petrov.yaplay.cache;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.nio.ByteBuffer;

final class CachedMediaValidator {
    private CachedMediaValidator() {
    }

    static boolean audio(File file, long expectedDurationMs) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            return audio(extractor, expectedDurationMs);
        } catch (Exception ex) {
            return false;
        } finally {
            extractor.release();
        }
    }

    static boolean audio(MediaExtractor extractor, long expectedDurationMs) {
        try {
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) {
                    continue;
                }
                extractor.selectTrack(i);
                long durationMs = expectedDurationMs;
                if (durationMs <= 0 && format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000;
                }
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
                long first = -1;
                long last = -1;
                long lastDelta = 0;
                int samples = 0;
                while (extractor.getSampleTime() >= 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        return false;
                    }
                    long size = extractor.getSampleSize();
                    if (size <= 0 || size > 4 * 1024 * 1024) {
                        return false;
                    }
                    if (buffer.capacity() < size) {
                        buffer = ByteBuffer.allocate((int) size);
                    }
                    buffer.clear();
                    if (extractor.readSampleData(buffer, 0) != size) {
                        return false;
                    }
                    long time = extractor.getSampleTime();
                    if (last > time) {
                        return false;
                    }
                    if (first < 0) {
                        first = time;
                    } else {
                        lastDelta = time - last;
                    }
                    last = time;
                    samples++;
                    if (!extractor.advance()) {
                        break;
                    }
                }
                // Walk real packets: a surviving ID3/Xing header alone is not a song.
                long readableMs = (last - first + lastDelta) / 1000;
                long toleranceMs = Math.max(2000, Math.min(5000, durationMs / 50));
                return samples > 0 && (durationMs <= 0 || readableMs + toleranceMs >= durationMs);
            }
        } catch (RuntimeException ex) {
            return false;
        }
        return false;
    }

    static boolean artwork(ImageDecoder.Source source) {
        try {
            Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, unused) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setOnPartialImageListener(error -> false);
                int largest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                decoder.setTargetSampleSize(Math.max(1, (largest + 511) / 512));
            });
            bitmap.recycle();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
