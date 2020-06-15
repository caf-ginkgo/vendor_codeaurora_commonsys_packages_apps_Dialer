/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.dialer.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.codeaurora.ims.CallComposerInfo;
import org.codeaurora.ims.QtiCallConstants;
import android.content.ContentResolver;

/** General purpose utility methods for the Dialer. */
public class DialerUtils {

  /**
   * Prefix on a dialed number that indicates that the call should be placed through the Wireless
   * Priority Service.
   */
  private static final String WPS_PREFIX = "*272";
  private static final String WPS_PREFIX_CLIR_ACTIVATE = "*31#*272";
  private static final String WPS_PREFIX_CLIR_DEACTIVATE = "#31#*272";

  public static final String FILE_PROVIDER_CACHE_DIR = "my_cache";

  private static final Random RANDOM = new Random();

  private static final HashMap<String, Integer> LOADER_ID_MAP = new HashMap<> ();

  /**
   * Attempts to start an activity and displays a toast with the default error message if the
   * activity is not found, instead of throwing an exception.
   *
   * @param context to start the activity with.
   * @param intent to start the activity with.
   */
  public static void startActivityWithErrorToast(Context context, Intent intent) {
    startActivityWithErrorToast(context, intent, R.string.activity_not_available);
  }

  /**
   * Attempts to start an activity and displays a toast with a provided error message if the
   * activity is not found, instead of throwing an exception.
   *
   * @param context to start the activity with.
   * @param intent to start the activity with.
   * @param msgId Resource ID of the string to display in an error message if the activity is not
   *     found.
   */
  public static void startActivityWithErrorToast(
      final Context context, final Intent intent, int msgId) {
    try {
      if ((Intent.ACTION_CALL.equals(intent.getAction()))) {
        // All dialer-initiated calls should pass the touch point to the InCallUI
        Point touchPoint = TouchPointManager.getInstance().getPoint();
        if (touchPoint.x != 0 || touchPoint.y != 0) {
          Bundle extras;
          // Make sure to not accidentally clobber any existing extras
          if (intent.hasExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
            extras = intent.getParcelableExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
          } else {
            extras = new Bundle();
          }
          extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
          intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
        }

        if (shouldWarnForOutgoingWps(context, intent.getData().getSchemeSpecificPart(), null)) {
          LogUtil.i(
              "DialerUtils.startActivityWithErrorToast",
              "showing outgoing WPS dialog before placing call");
          AlertDialog.Builder builder = new AlertDialog.Builder(context);
          builder.setMessage(R.string.outgoing_wps_warning);
          builder.setPositiveButton(
              R.string.dialog_continue,
              new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  placeCallOrMakeToast(context, intent);
                }
              });
          builder.setNegativeButton(android.R.string.cancel, null);
          builder.create().show();
        } else {
          placeCallOrMakeToast(context, intent);
        }
      } else {
        context.startActivity(intent);
      }
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
    }
  }

  private static void placeCallOrMakeToast(Context context, Intent intent) {
    final boolean hasCallPermission = TelecomUtil.placeCall(context, intent);
    if (!hasCallPermission) {
      // TODO: Make calling activity show request permission dialog and handle
      // callback results appropriately.
      Toast.makeText(context, "Cannot place call without Phone permission", Toast.LENGTH_SHORT)
          .show();
    }
  }

  /**
   *@return true if calllog inserted earlier when dial a ConfURI call.
   */
  public static boolean isConferenceURICallLog(String number, String postDialDigits) {
    return (number == null || number.contains(";") || number.contains(",")) &&
        TextUtils.isEmpty(postDialDigits);
  }

  /**
   * Returns whether the user should be warned about an outgoing WPS call. This checks if there is a
   * currently active call over LTE. Regardless of the country or carrier, the radio will drop an
   * active LTE call if a WPS number is dialed, so this warning is necessary.
   */
  @SuppressLint("MissingPermission")
  public static boolean shouldWarnForOutgoingWps(Context context, String number,
      PhoneAccountHandle phoneAccountHandle) {
    if (number != null && (number.startsWith(WPS_PREFIX) ||
        number.startsWith(WPS_PREFIX_CLIR_ACTIVATE) ||
        number.startsWith(WPS_PREFIX_CLIR_DEACTIVATE))) {
      TelephonyManager telephonyManager = getTelephonyManager(context, phoneAccountHandle);
      boolean isOnVolte =
          telephonyManager.getVoiceNetworkType() == TelephonyManager.NETWORK_TYPE_LTE;
      boolean hasCurrentActiveCall = telephonyManager.getCallState(
          getSubscriptionIdFromPhoneAccount(context, phoneAccountHandle)) ==
          TelephonyManager.CALL_STATE_OFFHOOK;
      return isOnVolte && hasCurrentActiveCall;
    }
    return false;
  }

  private static int getSubscriptionIdFromPhoneAccount(Context context,
      PhoneAccountHandle phoneAccountHandle) {
    SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);

    if (phoneAccountHandle == null || TextUtils.isEmpty(phoneAccountHandle.getId())) {
      LogUtil.i("DialerUtils.getSubscriptionIdFromPhoneAccount",
          "phoneAccountHandle is null or empty");
      return subscriptionManager.getDefaultSubscriptionId();
    }

    List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
    if (subscriptionInfos == null) {
      LogUtil.i("DialerUtils.getSubscriptionIdFromPhoneAccount", "SubscriptionInfo is null");
      return subscriptionManager.getDefaultSubscriptionId();
    }

    SubscriptionInfo subscriptionInfo = null;
    for (SubscriptionInfo info : subscriptionInfos) {
      if (phoneAccountHandle.getId().startsWith(info.getIccId())) {
        subscriptionInfo = info;
        break;
      }
    }

    return subscriptionInfo != null ? subscriptionInfo.getSubscriptionId() :
        subscriptionManager.getDefaultSubscriptionId();
  }

  private static TelephonyManager getTelephonyManager(Context context,
      PhoneAccountHandle phoneAccountHandle) {
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);

    if (phoneAccountHandle == null) {
      LogUtil.i("DialerUtils.getTelephonyManager", "phoneAccountHandle is null");
      return telephonyManager;
    }

    com.google.common.base.Optional<SubscriptionInfo> subscriptionInfo = TelecomUtil.
        getSubscriptionInfo(context, phoneAccountHandle);
    if (!subscriptionInfo.isPresent()) {
      LogUtil.i("DialerUtils.getTelephonyManager", "SubscriptionInfo is not valid");
      return telephonyManager;
    }

    TelephonyManager subSpecificTelManager = telephonyManager.createForSubscriptionId(
        subscriptionInfo.get().getSubscriptionId());
    if (subSpecificTelManager == null) {
      LogUtil.i("DialerUtils.getTelephonyManager", "createForSubscriptionId subSpecificTelManager" +
          " is null");
      return telephonyManager;
    }

    return subSpecificTelManager;
  }

  /**
   * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
   * null.
   *
   * @param closeable to close.
   */
  public static void closeQuietly(AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (RuntimeException rethrown) {
        throw rethrown;
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by ", ".
   *
   * @param list List of char sequences to join.
   * @return Joined char sequences.
   */
  public static CharSequence join(Iterable<CharSequence> list) {
    StringBuilder sb = new StringBuilder();
    final BidiFormatter formatter = BidiFormatter.getInstance();
    final CharSequence separator = ", ";

    Iterator<CharSequence> itr = list.iterator();
    boolean firstTime = true;
    while (itr.hasNext()) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(separator);
      }
      // Unicode wrap the elements of the list to respect RTL for individual strings.
      sb.append(
          formatter.unicodeWrap(itr.next().toString(), TextDirectionHeuristics.FIRSTSTRONG_LTR));
    }

    // Unicode wrap the joined value, to respect locale's RTL ordering for the whole list.
    return formatter.unicodeWrap(sb.toString());
  }

  public static void showInputMethod(View view) {
    final InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(view, 0);
    }
  }

  public static void hideInputMethod(View view) {
    final InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  /**
   * Create a File in the cache directory that Dialer's FileProvider knows about so they can be
   * shared to other apps.
   */
  public static File createShareableFile(Context context) {
    long fileId = Math.abs(RANDOM.nextLong());
    File parentDir = new File(context.getCacheDir(), FILE_PROVIDER_CACHE_DIR);
    if (!parentDir.exists()) {
      parentDir.mkdirs();
    }
    return new File(parentDir, String.valueOf(fileId));
  }

  public static int getLoaderId(String name) {
    if (TextUtils.isEmpty(name)) return 0;
    if (LOADER_ID_MAP.containsKey(name)) {
      return LOADER_ID_MAP.get(name);
    } else {
        final int index = LOADER_ID_MAP.size() + 1;
        LOADER_ID_MAP.put(name, index);
        return index;
    }
  }

  /**
   * Helper function which reads call composer data from Settings.Global and updates the bundle.
   *
   * Keys used to store/read call composer data to/from Settings.Global are defined in
   * QtiCallConstants. This is temporary solution to enable testing of this feaure.
   *
   * @see QtiCallConstants#EXTRA_CALL_COMPOSER_*
   *
   */
  public static Bundle maybeAddCallComposerExtras(@NonNull ContentResolver resolver,
                                                             @NonNull Bundle extras) {

    // Call composer data are parsed only when EXTRA_CALL_COMPOSER_INFO is present.
    String shouldParseCallComposerData = Settings.Global.getString(resolver,
                QtiCallConstants.EXTRA_CALL_COMPOSER_INFO);
    if (shouldParseCallComposerData == null || shouldParseCallComposerData.isEmpty()) {
        return extras;
    }

    // Update extras with call subjects.
    String subject = Settings.Global.getString(resolver,
                                QtiCallConstants.EXTRA_CALL_COMPOSER_SUBJECT);
    extras.putString(QtiCallConstants.EXTRA_CALL_COMPOSER_SUBJECT, subject);

    // Update extras with image URI.
    try {
        String image = Settings.Global.getString(resolver,
                                QtiCallConstants.EXTRA_CALL_COMPOSER_IMAGE);
        if (image != null && !image.isEmpty()) {
            extras.putParcelable(QtiCallConstants.EXTRA_CALL_COMPOSER_IMAGE, Uri.parse(image));
        }
    } catch (Exception e) {
      LogUtil.e("DialerUtils.maybeAddCallComposerExtras", "Invalid image URI, Exception: " + e);
    }

    // Update extras with call priority.
    try {
      int priority = Settings.Global.getInt(resolver, QtiCallConstants.EXTRA_CALL_COMPOSER_PRIORITY,
              CallComposerInfo.PRIORITY_NORMAL);
      extras.putInt(QtiCallConstants.EXTRA_CALL_COMPOSER_PRIORITY, priority);
    } catch (Exception e) {
      LogUtil.e("DialerUtils.maybeAddCallComposerExtras", "Invalid call priority, Exception: " + e);
    }

    // Update extras with call location.
    try {
      String sLat = Settings.Global.getString(resolver,
              QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_LATITUDE);
      String sLong = Settings.Global.getString(resolver,
              QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_LONGITUDE);
      String sRadius = Settings.Global.getString(resolver,
              QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_RADIUS);
      boolean isLocationInvalid = sLat == null || sLat.isEmpty()
            || sLong == null || sLong.isEmpty();
      if (!isLocationInvalid) {
          extras.putDouble(QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_LATITUDE,
              Double.parseDouble(sLat));
          extras.putDouble(QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_LONGITUDE,
              Double.parseDouble(sLong));
          if (sRadius != null && !sRadius.isEmpty()) {
            extras.putDouble(QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_RADIUS,
                Double.parseDouble(sRadius));
          }
          extras.putString(QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION, "");
      }
    } catch (Exception e) {
      LogUtil.e("DialerUtils.maybeAddCallComposerExtras", "Invalid call location, Exception: " + e);
    }

    // Mark call compser data as valid.
    extras.putString(QtiCallConstants.EXTRA_CALL_COMPOSER_INFO, "");

    return extras;
  }
}
