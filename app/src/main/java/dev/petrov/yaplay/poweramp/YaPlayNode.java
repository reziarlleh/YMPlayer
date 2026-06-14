package dev.petrov.yaplay.poweramp;

import dev.petrov.yaplay.ymusic.YandexMusicClient;

final class YaPlayNode {
    final String documentId;
    final String title;
    final boolean directory;
    final YandexMusicClient.Track track;

    static YaPlayNode folder(String documentId, String title) {
        return new YaPlayNode(documentId, title, true, null);
    }

    static YaPlayNode track(String documentId, YandexMusicClient.Track track) {
        return new YaPlayNode(documentId, track.title, false, track);
    }

    private YaPlayNode(String documentId, String title, boolean directory, YandexMusicClient.Track track) {
        this.documentId = documentId;
        this.title = title;
        this.directory = directory;
        this.track = track;
    }
}
