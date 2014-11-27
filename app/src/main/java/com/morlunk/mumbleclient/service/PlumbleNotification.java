/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.mumbleclient.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.NotificationCompat;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.DrawerAdapter;
import com.morlunk.mumbleclient.app.PlumbleActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper to create Plumble notifications.
 * Created by andrew on 08/08/14.
 */
public class PlumbleNotification {
    private static final int NOTIFICATION_ID = 1;
    private static final String BROADCAST_MUTE = "b_mute";
    private static final String BROADCAST_DEAFEN = "b_deafen";
    private static final String BROADCAST_OVERLAY = "b_overlay";
    private static final String BROADCAST_CANCEL_RECONNECT = "b_cancel_reconnect";

    private Service mService;
    private OnActionListener mListener;
    private List<String> mMessages;
    private String mCustomTicker;
    private String mCustomContentText;
    private boolean mReconnecting;

    private BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BROADCAST_MUTE.equals(intent.getAction())) {
                mListener.onMuteToggled();
            } else if (BROADCAST_DEAFEN.equals(intent.getAction())) {
                mListener.onDeafenToggled();
            } else if (BROADCAST_OVERLAY.equals(intent.getAction())) {
                mListener.onOverlayToggled();
            } else if (BROADCAST_CANCEL_RECONNECT.equals(intent.getAction())) {
                mListener.onReconnectCanceled();
            }
        }
    };

    /**
     * Creates a foreground Plumble notification for the given service.
     * @param service The service to register a foreground notification for.
     * @param listener An listener for notification actions.
     * @return A new PlumbleNotification instance.
     */
    public static PlumbleNotification showForeground(Service service, OnActionListener listener) {
        PlumbleNotification notification = new PlumbleNotification(service, listener);
        notification.show();
        return notification;
    }

    private PlumbleNotification(Service service, OnActionListener listener) {
        mService = service;
        mListener = listener;
        mMessages = new ArrayList<String>();
        mCustomTicker = mService.getString(R.string.plumbleConnected);
        mCustomContentText = mService.getString(R.string.connected);
        mReconnecting = false;
    }

    public void setCustomTicker(String ticker) {
        mCustomTicker = ticker;
        createNotification();
    }

    public void setCustomContentText(String text) {
        mCustomContentText = text;
        createNotification();
    }

    public void setReconnecting(boolean reconnecting) {
        mReconnecting = reconnecting;
        createNotification();
    }

    /**
     * Updates the notification with the given message.
     * Sets the ticker to the current message as well.
     * @param message The message to notify.
     */
    public void addMessage(String message) {
        mMessages.add(message);
        mCustomTicker = message;
        createNotification();
    }

    public void clearMessages() {
        mMessages.clear();
        createNotification();
    }

    /**
     * Shows the notification and registers the notification action button receiver.
     */
    public void show() {
        createNotification();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_DEAFEN);
        filter.addAction(BROADCAST_MUTE);
        filter.addAction(BROADCAST_OVERLAY);
        filter.addAction(BROADCAST_CANCEL_RECONNECT);
        try {
            mService.registerReceiver(mNotificationReceiver, filter);
        } catch (IllegalArgumentException e) {
            // Thrown if receiver is already registered.
            e.printStackTrace();
        }
    }

    /**
     * Hides the notification and unregisters the action receiver.
     */
    public void hide() {
        try {
            mService.unregisterReceiver(mNotificationReceiver);
        } catch (IllegalArgumentException e) {
            // Thrown if receiver is not registered.
            e.printStackTrace();
        }
        mService.stopForeground(true);
    }

    /**
     * Called to update/create the service's foreground Plumble notification.
     */
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setTicker(mCustomTicker);
        builder.setContentTitle(mService.getString(R.string.app_name));
        builder.setContentText(mCustomContentText);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOngoing(true);

        if (!mReconnecting) {
            // Add notification triggers
            Intent muteIntent = new Intent(BROADCAST_MUTE);
            Intent deafenIntent = new Intent(BROADCAST_DEAFEN);
            Intent overlayIntent = new Intent(BROADCAST_OVERLAY);

            builder.addAction(R.drawable.ic_action_microphone,
                    mService.getString(R.string.mute), PendingIntent.getBroadcast(mService, 1,
                            muteIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.addAction(R.drawable.ic_action_audio,
                    mService.getString(R.string.deafen), PendingIntent.getBroadcast(mService, 1,
                            deafenIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.addAction(R.drawable.ic_action_channels,
                    mService.getString(R.string.overlay), PendingIntent.getBroadcast(mService, 2,
                            overlayIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        } else if (!PlumbleActivity.KioskMode){
            Intent cancelIntent = new Intent(BROADCAST_CANCEL_RECONNECT);
            builder.addAction(R.drawable.ic_action_delete_dark,
                    mService.getString(R.string.cancel_reconnect), PendingIntent.getBroadcast(mService, 2,
                            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        }

        // Show unread messages
        if (mMessages.size() > 0) {
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            for (String message : mMessages) {
                inboxStyle.addLine(message);
            }
            builder.setStyle(inboxStyle);
        }

        Intent channelListIntent = new Intent(mService, PlumbleActivity.class);
        channelListIntent.putExtra(PlumbleActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER);
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0, channelListIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);

        Notification notification = builder.build();
        mService.startForeground(NOTIFICATION_ID, notification);
        return notification;
    }

    public interface OnActionListener {
        public void onMuteToggled();
        public void onDeafenToggled();
        public void onOverlayToggled();
        public void onReconnectCanceled();
    }
}
