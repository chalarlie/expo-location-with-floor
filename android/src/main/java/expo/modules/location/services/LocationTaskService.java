package expo.modules.location.services;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;

public class LocationTaskService extends Service {
  private static final String TAG = "LocationTaskService";
  private static int sServiceId = 481756;

  private String mChannelId;
  private Context mParentContext;
  private int mServiceId = sServiceId++;
  private final IBinder mBinder = new ServiceBinder();

  private static final String META_DATA_DEFAULT_ICON_KEY = "expo.modules.notifications.default_notification_icon";
  public static final String META_DATA_DEFAULT_COLOR_KEY = "expo.modules.notifications.default_notification_color";

  public class ServiceBinder extends Binder {
    public LocationTaskService getService() {
      return LocationTaskService.this;
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    Log.w(TAG, "onBind");
    return mBinder;
  }

  @Override
  @TargetApi(26)
  public int onStartCommand(Intent intent, int flags, int startId) {
    Bundle extras = intent.getExtras();

    if (extras != null) {
      mChannelId = extras.getString("appId") + ":" + extras.getString("taskName");
    }

    return START_REDELIVER_INTENT;
  }

  public void setParentContext(Context context) {
    // Background location logic is still outside LocationTaskService,
    // so we have to save parent context in order to make sure it won't be destroyed by the OS.
    mParentContext = context;
  }

  public void stop() {
    stopForeground(true);
    stopSelf();
  }

  public void startForeground(Bundle serviceOptions) {
    Notification notification = buildServiceNotification(serviceOptions);
    startForeground(mServiceId, notification);
  }

  //region private

  private int getIcon() {
    try {
      ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
      if (ai.metaData.containsKey(META_DATA_DEFAULT_ICON_KEY)) {
        return ai.metaData.getInt(META_DATA_DEFAULT_ICON_KEY);
      }
    } catch (PackageManager.NameNotFoundException | ClassCastException e) {
      Log.e("expo-location", "Could not have fetched default notification icon.");
    }
    return getApplicationInfo().icon;
  }

  @Nullable
  private Integer getIconColor() {
    try {
      ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
      if (ai.metaData.containsKey(META_DATA_DEFAULT_COLOR_KEY)) {
          return getResources().getColor(ai.metaData.getInt(META_DATA_DEFAULT_COLOR_KEY), null);
        }
    } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | ClassCastException e) {
      Log.e("expo-location", "Could not have fetched default notification icon color.");
    }

    // No custom color
    return null;
  }

  @TargetApi(26)
  private Notification buildServiceNotification(Bundle serviceOptions) {
    prepareChannel(mChannelId);

    Notification.Builder builder = new Notification.Builder(this, mChannelId);

    String title = serviceOptions.getString("notificationTitle");
    String body = serviceOptions.getString("notificationBody");
    Integer color = colorStringToInteger(serviceOptions.getString("notificationColor"));

    if (title != null) {
      builder.setContentTitle(title);
    }
    if (body != null) {
      builder.setContentText(body);
    }
    if (color != null) {
      builder.setColorized(true).setColor(color);
    } else {
      builder.setColorized(false);
    }

    Intent intent = mParentContext.getPackageManager().getLaunchIntentForPackage(mParentContext.getPackageName());

    if (intent != null) {
      intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
      builder.setContentIntent(contentIntent);
    }

    return builder.setCategory(Notification.CATEGORY_SERVICE)
        .setSmallIcon(getIcon())
        .setColor(getIconColor())
        .build();
  }

  @TargetApi(26)
  private void prepareChannel(String id) {
    NotificationManager notificationManager = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);

    if (notificationManager != null) {
      String appName = getApplicationInfo().loadLabel(getPackageManager()).toString();
      NotificationChannel channel = notificationManager.getNotificationChannel(id);

      if (channel == null) {
        channel = new NotificationChannel(id, appName, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Background location notification channel");
        notificationManager.createNotificationChannel(channel);
      }
    }
  }

  private Integer colorStringToInteger(String color) {
    try {
      return Color.parseColor(color);
    } catch (Exception e) {
      return null;
    }
  }

  //endregion
}
