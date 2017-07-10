package com.nauto.example.cameramodule;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.nauto.camera.Utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import allbegray.slack.SlackClientFactory;
import allbegray.slack.exception.SlackResponseErrorException;
import allbegray.slack.rtm.Event;
import allbegray.slack.rtm.EventListener;
import allbegray.slack.rtm.SlackRealTimeMessagingClient;
import allbegray.slack.type.Authentication;
import allbegray.slack.type.Channel;
import allbegray.slack.type.User;
import allbegray.slack.webapi.SlackWebApiClient;
import android.util.Log;

/**
 * Created by ab on 7/8/17.
 */

public class Messenger {
    final String slackToken = "xoxb-209424457346-CJQGap1sDbeFyQeXaoESPY8N";
    public static final String TAG = Messenger.class.getSimpleName();

    private static Messenger mInstance;
    public static Messenger getInstance() {
        return mInstance ==  null ? new Messenger() : mInstance;
    }

    private SlackWebApiClient mWebApiClient;
    private SlackRealTimeMessagingClient mRtmClient;
    private String mBotId;
    private String mChannelId;

    private Messenger() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mWebApiClient = SlackClientFactory.createWebApiClient(slackToken);
                String webSocketUrl = mWebApiClient.startRealTimeMessagingApi().findPath("url").asText();
                mRtmClient = new SlackRealTimeMessagingClient(webSocketUrl);

                mRtmClient.addListener(Event.HELLO, new EventListener() {
                    @Override
                    public void onMessage(JsonNode message) {
                        Authentication authentication = mWebApiClient.auth();
                        mBotId = authentication.getUser_id();
                        String channelId = message.findPath("channel").asText();
                        Log.d(TAG, "User id: " + mBotId);
                        Log.d(TAG, "Team name: " + authentication.getTeam());
                        Log.d(TAG, "User name: " + authentication.getUser());
                    }
                });

                mRtmClient.addListener(Event.MESSAGE, new EventListener() {
                    @Override
                    public void onMessage(JsonNode message) {
                        String channelId = message.findPath("channel").asText();
                        String userId = message.findPath("user").asText();
                        String text = message.findPath("text").asText();

                        if (userId != null && !userId.equals(mBotId)) {
                            Channel channel;
                            try {
                                channel = mWebApiClient.getChannelInfo(channelId);
                            } catch (SlackResponseErrorException e) {
                                channel = null;
                            }
                            User user = mWebApiClient.getUserInfo(userId);
                            String userName = user.getName();
                            mChannelId = channelId;
                            Log.d(TAG, "Channel id: " + channelId);
                            Log.d(TAG, "Channel name: " + (channel != null ? "#" + channel.getName() : "DM"));
                            Log.d(TAG, "User id: " + userId);
                            Log.d(TAG, "User name: " + userName);
                            Log.d(TAG, "Text: " + text);
                            String s = Utils.getDeviceUid();
                            if (text != null && text.startsWith(s)) {
                                // Copy cat
                                String cmd = text.substring(s.length());
                                if (cmd.contains("upload")) {
                                    String fileName = null;
                                    if (fileName != null) {
                                        mWebApiClient.meMessage(channelId, userName + ": " + "uploading... ");
                                        Log.d(TAG, "uploading " + fileName);
                                        File f = new File(fileName);
                                        allbegray.slack.type.File res = mWebApiClient.uploadFile(f, "--", "video", channelId);
                                        Log.d(TAG, "file link:" + res.getPermalink_public());
                                        mWebApiClient.meMessage(channelId, "file link:" + res.getPermalink_public());
                                    }
                                }
                            } else {
                                mWebApiClient.meMessage(channelId, Utils.getDeviceUid() + ": " + text);
                            }
                        }
                    }
                });
                mRtmClient.connect();

            }
        }).start();
    }

    public void reportError(final String err) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mWebApiClient != null && mChannelId != null) {
                    try {
                        mWebApiClient.meMessage(mChannelId, Utils.getDeviceUid() + ":" + err);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private Message getErrorMessage(int errCode, String errMsg) {
        Message message = new Message()
                .withText("Camera " + Utils.getDeviceUid())
                .withAttachments(Arrays.asList(
                        new Message.Attachment()
                                // footer
                                // image url
                                // thumbnail url
                                // footer icon url
                                .withTime(System.currentTimeMillis())
                                .withPreText("Camera error code " + errCode)
                                .withText(errMsg)
                                .withFallback("TODO fallback ")
                                .withCallbackId("TODO callbackId ")
                                .withColor("#FF0000")
                                .withAttachmentType("")
                                .withActions(Arrays.asList(
                                        new Message.Action()
                                                .withName("")
                                                .withType("")
                                                .withStyle("")
                                                .withText("")
                                                .withValue("")
                                                .withConfirm(new Message.Confirm()
                                                        .withTitle("")
                                                        .withText("")
                                                        .withOkText("")
                                                        .withDismissText("")
                                                ),
                                        new Message.Action()
                                                .withName("")
                                                .withType("")
                                                .withStyle("")
                                                .withText("")
                                                .withValue("")
                                                .withConfirm(new Message.Confirm()
                                                        .withTitle("")
                                                        .withText("")
                                                        .withOkText("")
                                                        .withDismissText("")
                                                )
                                ))
                ));
        return message;
    }

    public static class Message {
        @SerializedName("text")
        @Expose
        public String text;
        @SerializedName("attachments")
        @Expose
        public List<Attachment> attachments = null;

        public Message withText(String text) {
            this.text = text;
            return this;
        }

        public Message withAttachments(List<Attachment> attachments) {
            this.attachments = attachments;
            return this;
        }

        public static class Attachment {

            @SerializedName("pretext")
            @Expose
            public String pretext;
            @SerializedName("text")
            @Expose
            public String text;
            @SerializedName("fallback")
            @Expose
            public String fallback;
            @SerializedName("callback_id")
            @Expose
            public String callbackId;
            @SerializedName("color")
            @Expose
            public String color;
            @SerializedName("attachment_type")
            @Expose
            public String attachmentType;
            @SerializedName("actions")
            @Expose
            public List<Action> actions = null;
            @SerializedName("ts")
            @Expose
            public long ts;

            public Attachment withPreText(String pretext) {
                this.pretext = pretext;
                return this;
            }

            public Attachment withText(String text) {
                this.text = text;
                return this;
            }

            public Attachment withFallback(String fallback) {
                this.fallback = fallback;
                return this;
            }

            public Attachment withCallbackId(String callbackId) {
                this.callbackId = callbackId;
                return this;
            }

            public Attachment withColor(String color) {
                this.color = color;
                return this;
            }

            public Attachment withAttachmentType(String attachmentType) {
                this.attachmentType = attachmentType;
                return this;
            }

            public Attachment withActions(List<Action> actions) {
                this.actions = actions;
                return this;
            }

            public Attachment withTime(long ts) {
                this.ts = ts;
                return this;
            }
        }

        public static class Action {
            @SerializedName("name")
            @Expose
            public String name;
            @SerializedName("text")
            @Expose
            public String text;
            @SerializedName("type")
            @Expose
            public String type;
            @SerializedName("value")
            @Expose
            public String value;
            @SerializedName("style")
            @Expose
            public String style;
            @SerializedName("confirm")
            @Expose
            public Confirm confirm;

            public Action withName(String name) {
                this.name = name;
                return this;
            }

            public Action withText(String text) {
                this.text = text;
                return this;
            }

            public Action withType(String type) {
                this.type = type;
                return this;
            }

            public Action withValue(String value) {
                this.value = value;
                return this;
            }

            public Action withStyle(String style) {
                this.style = style;
                return this;
            }

            public Action withConfirm(Confirm confirm) {
                this.confirm = confirm;
                return this;
            }

        }

        public static class Confirm {

            @SerializedName("title")
            @Expose
            public String title;
            @SerializedName("text")
            @Expose
            public String text;
            @SerializedName("ok_text")
            @Expose
            public String okText;
            @SerializedName("dismiss_text")
            @Expose
            public String dismissText;

            public Confirm withTitle(String title) {
                this.title = title;
                return this;
            }

            public Confirm withText(String text) {
                this.text = text;
                return this;
            }

            public Confirm withOkText(String okText) {
                this.okText = okText;
                return this;
            }

            public Confirm withDismissText(String dismissText) {
                this.dismissText = dismissText;
                return this;
            }

        }
    }
}
