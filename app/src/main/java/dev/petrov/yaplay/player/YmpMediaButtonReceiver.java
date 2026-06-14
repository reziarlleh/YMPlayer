package dev.petrov.yaplay.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class YmpMediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }
        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_UP) {
            return;
        }
        String action;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                action = YmpPlaybackService.ACTION_NEXT;
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                action = YmpPlaybackService.ACTION_PREVIOUS;
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                action = YmpPlaybackService.ACTION_STOP;
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            default:
                action = YmpPlaybackService.ACTION_PLAY_PAUSE;
                break;
        }
        Intent service = new Intent(context, YmpPlaybackService.class);
        service.setAction(action);
        context.startForegroundService(service);
    }
}
