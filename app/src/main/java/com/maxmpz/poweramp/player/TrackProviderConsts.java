/*
Copyright (C) 2011-2020 Maksim Petrov

Redistribution and use in source and binary forms, with or without
modification, are permitted for the widgets, plugins, applications and other software
which communicate with Poweramp application on Android platform.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.
*/
package com.maxmpz.poweramp.player;

public final class TrackProviderConsts {
    public static final String COLUMN_ALBUM_ARTIST = "album_artist";
    public static final String COLUMN_GENRE = "genre";
    public static final String COLUMN_TRACK_ALT = "track_alt";
    public static final String COLUMN_BITS_PER_SAMPLE = "bits_per_sample";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_TRACK_LYRICS = "lyrics";
    public static final String COLUMN_TRACK_LYRICS_SYNCED = "lyrics_synced";
    public static final String COLUMN_TRACK_WAVE = "track_wave";
    public static final String COLUMN_HEADERS = "headers";
    public static final String COLUMN_COOKIES = "cookies";
    public static final String COLUMN_HTTP_METHOD = "method";
    public static final String COLUMN_FLAGS = "__flags";

    public static final int FLAG_NO_SUBDIRS = 0x0001;
    public static final int FLAG_HAS_SUBDIRS = 0x0002;
    public static final int FLAG_HAS_LYRICS = 0x0004;

    public static final String DYNAMIC_URL = "__dynamic_url";
    public static final String CALL_GET_URL = "com.maxmpz.audioplayer:get_url";
    public static final String CALL_RESCAN = "com.maxmpz.audioplayer:rescan";
    public static final String CALL_GET_DIR_METADATA = "com.maxmpz.audioplayer:get_dir_metadata";
    public static final String EXTRA_ANCESTORS = "ancestors";
    public static final String SOURCE_INFO_TAGS = "info_tags";

    private TrackProviderConsts() {
    }
}
