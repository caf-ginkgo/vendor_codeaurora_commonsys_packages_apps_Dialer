/* Copyright (c) 2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.dialer.app;

import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.android.dialer.common.LogUtil;

import org.codeaurora.ims.CallComposerInfo;
import org.codeaurora.ims.QtiCallConstants;

/**
 * Receives broadcasts that notify of call composer elements.
 */
public class CallComposerNotificationReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "CallComposerNotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(QtiCallConstants.ACTION_CALL_COMPOSER_INFO)) {
            LogUtil.i(LOG_TAG, "onReceive: received call composer info notification");
            Bundle extras = intent.getBundleExtra(
                    QtiCallConstants.EXTRA_CALL_COMPOSER_INFO);
            LogUtil.d(LOG_TAG, "CallComposerInfo: " + toCallComposer(extras));
        }
    }

    private CallComposerInfo toCallComposer(Bundle extras) {
        CallComposerInfo.Location location = CallComposerInfo.Location.UNKNOWN;
        String subject = extras.getString(
                QtiCallConstants.EXTRA_CALL_COMPOSER_SUBJECT, null);
        int priority = extras.getInt(
                QtiCallConstants.EXTRA_CALL_COMPOSER_PRIORITY,
                CallComposerInfo.PRIORITY_NORMAL);
        Uri imageUrl = extras.getParcelable(
                QtiCallConstants.EXTRA_CALL_COMPOSER_IMAGE);
        if (extras.containsKey(QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION)) {
            float radius = extras.getFloat(
                    QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_RADIUS,
                    CallComposerInfo.Location.DEFAULT_RADIUS);
            double latitude = extras.getDouble(
                    QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_LATITUDE);
            double longitude = extras.getDouble(
                    QtiCallConstants.EXTRA_CALL_COMPOSER_LOCATION_LONGITUDE);
            location = new CallComposerInfo.Location(radius, latitude, longitude);
        }
        return new CallComposerInfo(priority, subject, imageUrl, location);
    }
}
