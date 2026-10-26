package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself. Android only lets an app read other apps' media sessions (MusicPanel) once it
 * holds a notification listener, so this is the component that gets the access.
 */
public class MusicListenerService extends NotificationListenerService {
}
