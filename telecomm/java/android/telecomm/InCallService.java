/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.telecomm;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import com.android.internal.os.SomeArgs;
import com.android.internal.telecomm.IInCallAdapter;
import com.android.internal.telecomm.IInCallService;

/**
 * This service is implemented by any app that wishes to provide the user-interface for managing
 * phone calls. Telecomm binds to this service while there exists a live (active or incoming)
 * call, and uses it to notify the in-call app of any live and and recently disconnected calls.
 * TODO(santoscordon): Needs more/better description of lifecycle once the interface is better
 * defined.
 * TODO(santoscordon): What happens if two or more apps on a given device implement this interface?
 */
public abstract class InCallService extends Service {
    private static final int MSG_SET_IN_CALL_ADAPTER = 1;
    private static final int MSG_ADD_CALL = 2;
    private static final int MSG_SET_ACTIVE = 3;
    private static final int MSG_SET_DISCONNECTED = 4;
    private static final int MSG_SET_HOLD = 5;
    private static final int MSG_ON_AUDIO_STATE_CHANGED = 6;
    private static final int MSG_SET_DIALING = 7;
    private static final int MSG_SET_RINGING = 8;
    private static final int MSG_SET_POST_DIAL = 9;
    private static final int MSG_SET_POST_DIAL_WAIT = 10;

    /** Default Handler used to consolidate binder method calls onto a single thread. */
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_IN_CALL_ADAPTER:
                    InCallAdapter adapter = new InCallAdapter((IInCallAdapter) msg.obj);
                    setInCallAdapter(adapter);
                    break;
                case MSG_ADD_CALL:
                    addCall((CallInfo) msg.obj);
                    break;
                case MSG_SET_ACTIVE:
                    setActive((String) msg.obj);
                    break;
                case MSG_SET_DIALING:
                    setDialing((String) msg.obj);
                    break;
                case MSG_SET_RINGING:
                    setRinging((String) msg.obj);
                    break;
                case MSG_SET_POST_DIAL: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String callId = (String) args.arg1;
                        String remaining = (String) args.arg2;
                        setPostDial(callId, remaining);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_POST_DIAL_WAIT: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String callId = (String) args.arg1;
                        String remaining = (String) args.arg2;
                        setPostDialWait(callId, remaining);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_DISCONNECTED:
                    setDisconnected((String) msg.obj, msg.arg1);
                    break;
                case MSG_SET_HOLD:
                    setOnHold((String) msg.obj);
                    break;
                case MSG_ON_AUDIO_STATE_CHANGED:
                    onAudioStateChanged((CallAudioState) msg.obj);
                default:
                    break;
            }
        }
    };

    /** Manages the binder calls so that the implementor does not need to deal with it. */
    private final class InCallServiceBinder extends IInCallService.Stub {
        /** {@inheritDoc} */
        @Override
        public void setInCallAdapter(IInCallAdapter inCallAdapter) {
            mHandler.obtainMessage(MSG_SET_IN_CALL_ADAPTER, inCallAdapter).sendToTarget();
        }

        /** {@inheritDoc} */
        @Override
        public void addCall(CallInfo callInfo) {
            mHandler.obtainMessage(MSG_ADD_CALL, callInfo).sendToTarget();
        }

        /** {@inheritDoc} */
        @Override
        public void setActive(String callId) {
            mHandler.obtainMessage(MSG_SET_ACTIVE, callId).sendToTarget();
        }

        /** {@inheritDoc} */
        @Override
        public void setDisconnected(String callId, int disconnectCause) {
            mHandler.obtainMessage(MSG_SET_DISCONNECTED, disconnectCause, 0, callId).sendToTarget();
        }

        /** {@inheritDoc} */
        @Override
        public void setOnHold(String callId) {
            mHandler.obtainMessage(MSG_SET_HOLD, callId).sendToTarget();
        }

        /** {@inheritDoc} */
        @Override
        public void onAudioStateChanged(CallAudioState audioState) {
            mHandler.obtainMessage(MSG_ON_AUDIO_STATE_CHANGED, audioState).sendToTarget();
        }

        @Override
        public void setDialing(String callId) {
            mHandler.obtainMessage(MSG_SET_DIALING, callId).sendToTarget();
        }

        @Override
        public void setRinging(String callId) {
            mHandler.obtainMessage(MSG_SET_RINGING, callId).sendToTarget();
        }

        @Override
        public void setPostDial(String callId, String remaining) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = remaining;
            mHandler.obtainMessage(MSG_SET_POST_DIAL, args).sendToTarget();
        }

        @Override
        public void setPostDialWait(String callId, String remaining) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = remaining;
            mHandler.obtainMessage(MSG_SET_POST_DIAL_WAIT, args).sendToTarget();
        }
    }

    private final InCallServiceBinder mBinder;

    protected InCallService() {
        mBinder = new InCallServiceBinder();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Provides the in-call app an adapter object through which to send call-commands such as
     * answering and rejecting incoming calls, disconnecting active calls, and putting calls in
     * special states (mute, hold, etc).
     *
     * @param inCallAdapter Adapter through which an in-call app can send call-commands to Telecomm.
     */
    protected abstract void setInCallAdapter(InCallAdapter inCallAdapter);

    /**
     * Indicates to the in-call app that a new call has been created and an appropriate
     * user-interface should be built and shown to notify the user. Information about the call
     * including its current state is passed in through the callInfo object.
     *
     * @param callInfo Information about the new call.
     */
     protected abstract void addCall(CallInfo callInfo);

    /**
     * Indicates to the in-call app that the specified call is currently connected to another party
     * and a communication channel is open between them. Normal transitions are to
     * {@link #setDisconnected(String)} when the call is complete.
     *
     * @param callId The identifier of the call changing state.
     */
    protected abstract void setActive(String callId);

    /**
     * Indicates to the in-call app that the specified call is outgoing and in the dialing state.
     * Normal transition are to {@link #setActive(String)} if the call was answered,
     * {@link #setPostDial(String,String)} if the dialed number includes a post-dial DTMF string, or
     * {@link #setDisconnected(String)} if the call was disconnected immediately.
     *
     * @param callId The identifier of the call changing state.
     */
    protected abstract void setDialing(String callId);

    /**
     * Indicates to the in-call app that the specified call is incoming and the user still has the
     * option of answering, rejecting, or doing nothing with the call. This state is usually
     * associated with some type of audible ringtone. Normal transitions are to
     * {@link #setActive(String)} if the call is answered, or {@link #setDisconnected(String)} if
     * the call is not answered or is otherwise disconnected for some reason.
     *
     * @param callId The identifier of the call changing state.
     */
    protected abstract void setRinging(String callId);

    /**
     * Indicates to the in-call app that a call has been moved to the
     * {@link CallState#DISCONNECTED} and the user should be notified.
     *
     * @param callId The identifier of the call that was disconnected.
     * @param disconnectCause The reason for the disconnection, any of
     *         {@link android.telephony.DisconnectCause}.
     */
    protected abstract void setDisconnected(String callId, int disconnectCause);

    /**
     * Indicates to the in-call app that a call has been moved to the
     * {@link android.telecomm.CallState#ON_HOLD} state and the user should be notified.
     *
     * @param callId The identifier of the call that was put on hold.
     */
    protected abstract void setOnHold(String callId);

    /**
     * Called when the audio state changes.
     *
     * @param audioState The new {@link CallAudioState}.
     */
    protected abstract void onAudioStateChanged(CallAudioState audioState);

    /**
     * Indicates to the in-call app that the specified call is active but in a "post-dial" state
     * where Telecomm is now sending some dual-tone multi-frequency signaling (DTMF) tones appended
     * to the dialed number. Normal transitions are to {@link #setPostDialWait(String,String)} when
     * the post-dial string requires user confirmation to proceed, {@link #setActive(String)} when
     * the post-dial tones are completed, or {@link #setDisconnected(String)} if the call is
     * disconnected.
     *
     * @param callId The identifier of the call changing state.
     * @param remaining The remaining postdial string to be dialed.
     */
    protected abstract void setPostDial(String callId, String remaining);

    /**
     * Indicates to the in-call app that the specified call was in the
     * {@link #setPostDial(String,String)} state but is now waiting for user confirmation before the
     * remaining digits can be sent. Normal transitions are to {@link #setPostDial(String,String)}
     * when the user asks Telecomm to proceed with the post-dial sequence and the in-call app
     * informs Telecomm of this by invoking {@link IInCallAdapter#postDialContinue(String)}.
     *
     * @param callId The identifier of the call changing state.
     * @param remaining The remaining postdial string to be dialed.
     */
    protected abstract void setPostDialWait(String callId, String remaining);
}
